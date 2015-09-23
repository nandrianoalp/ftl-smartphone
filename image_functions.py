from __future__ import print_function
from PIL import Image
from PIL.ExifTags import TAGS
import numpy as np
import sys

def get_exif(filename):
    """Get the exif header for filename, return in a dictionary. """
    ret = {}
    i = Image.open(filename)
    metadata = i._getexif()
    for tag, value in metadata.items():
        decoded = TAGS.get(tag, tag)
        ret[decoded] = value 
    return ret
        
def exif_gps(exif_data):
    """Return just the GPSInfo from the exif dictionary."""
    return exif_data['GPSInfo']
    
def exif_dms_to_dd(my_gps):
    """Convert deg,min,sec lat,lon to decimal degrees, return dictionary."""
    lat = [float(x)/float(y) for x, y in my_gps[2]]
    latref = my_gps[1]
    lon = [float(x)/float(y) for x, y in my_gps[4]]
    lonref = my_gps[3]

    lat = lat[0] + lat[1]/60 + lat[2]/3600
    lon = lon[0] + lon[1]/60 + lon[2]/3600
    if latref == 'S':
        lat = -lat
    if lonref == 'W':
        lon = -lon
        
    return {'latitude':lat, 'longitude':lon}

def calculate_fov(exif_data):
    """Caluclate horizontal and vertical field of view in radians, return dictionary."""
    if exif_data['Model'] == u'XT1031':
        pixel_size = 1.4e-6 #for Moto G pixels are 1.4 micrometer https://www.aptina.com/PowerSolutions/product.do?id=AR0543
    elif exif_data['Model'] == u'SAMSUNG-SM-G900A':
        pixel_size = 1.34e-6 #the pixel size for the S5 - used S4 value https://www.chipworks.com/about-chipworks/overview/blog/inside-samsung-galaxy-s4
    elif exif_data['Model'] == u'GT-I9300': #Galaxy S3
        pixel_size = 1.4e-6 #the internet suggests 1.4 micron http://www.phonearena.com/news/Samsung-Galaxy-S-III-torn-down-has-same-camera-sensor-as-Apple-iPhone-4S_id30837
    else:
        pixel_size = 1.4e-6
        print("Uh oh")
    #get focal length in meters.  Note that the exif contains a ratio, and teh standard specifies mm
    #so, the 1000.0 is to go from mm to meters, and the exif_data division should give a focal length
    #value in mm
    focal_length = float(exif_data['FocalLength'][0])/float(exif_data['FocalLength'][1])/1000.0
    #get sensor width and height in meters
    width = float(exif_data['ExifImageWidth'])*pixel_size
    height = float(exif_data['ExifImageHeight'])*pixel_size 
    #equations
    #https://en.wikipedia.org/wiki/Angle_of_view
    horizontal_fov = 2*np.arctan(width/2.0/focal_length)
    vertical_fov = 2*np.arctan(height/2.0/focal_length)
    
    return {'horizontal_fov':horizontal_fov, 'vertical_fov':vertical_fov}
    
def calculate_degrees_to_box_edge(fov, altitude):
    """Calculate the number degrees to the edge of the box with fov, altituve, return dictionary."""
    R_E = 6371.0e3 #radius is 6,371 kilometers
    horizontal_degrees = altitude*np.arctan(fov['horizontal_fov']/2.0) * 180.0/(np.pi*R_E)
    vertical_degrees = altitude*np.arctan(fov['vertical_fov']/2.0) * 180.0/(np.pi*R_E)
    
    return {'horizontal_degrees':horizontal_degrees, 'vertical_degrees':vertical_degrees}
    
def create_image_bounds(center, degrees_to_edge):
    """Calculate box edges, assuming no rotation.  Return dictionary."""
    #need to account for rotation?  Or does GE take the bound and then rotate.
    #Assuming the latter for now
    north = center['latitude'] + degrees_to_edge['vertical_degrees']
    south = center['latitude'] - degrees_to_edge['vertical_degrees']
    east = center['longitude'] + degrees_to_edge['horizontal_degrees']
    west = center['longitude'] - degrees_to_edge['horizontal_degrees']
    
    return {'north':north, 'south':south, 'east':east, 'west':west}
    
def create_rotations_dict(filename):
    """Read in the sesor data and create a lookup dictionary with imagenames as keys for rotation angle."""
    #azimuth = 0 => north, 90 => east, 180 => south, 270 => west
    #http://developer.android.com/reference/android/hardware/SensorEvent.html
    #but what about a 90 deg rotation to be in landscape v portrait mode?
    image_files = []
    image_rotations = []
    rotations_dictionary = {}
    checker = 0
    with open(filename, 'r') as file:
        for line in file:
            if ".jpg" in line:
                image_files.append(line.strip('\r\n'))
                checker = checker + 1
            if "Azimuth" in line:
                value = line.split(': ')[-1]
                value = value.split(';')[0]
                image_rotations.append(value)
                checker = checker + 1
                
    if (len(image_files) != len(image_rotations)):
        print("Image metadata not correct in file!")
        sys.exit()

    for index, image_name in enumerate(image_files):
        rotations_dictionary[image_name] = image_rotations[index]
        
    return rotations_dictionary