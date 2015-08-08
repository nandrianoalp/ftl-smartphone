from __future__ import print_function
from PIL import Image
from PIL.ExifTags import TAGS
import os
import alpkml
import numpy as np

def get_exif(fn):
    ret = {}
    i = Image.open(fn)
    metadata = i._getexif()
    for tag, value in metadata.items():
        decoded = TAGS.get(tag, tag)
        ret[decoded] = value 
    return ret
        
def exif_gps(exif_data):
    return exif_data['GPSInfo']
    
def exif_dms_to_dd(my_gps):
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

#horizontal and vertical field of view in radians
def calculate_fov(exif_data):
    #return field of view in radians
    if exif_data['Model'] == u'XT1031':
        pixel_size = 1.4e-6 #for Moto G pixels are 1.4 micrometer https://www.aptina.com/PowerSolutions/product.do?id=AR0543
    elif exif_data['Model'] == u'SAMSUNG-SM-G900A':
        pixel_size = 1.34e-6 #the pixel size for the S4 is... https://www.chipworks.com/about-chipworks/overview/blog/inside-samsung-galaxy-s4
    #elif: Galaxy S3
    #    pixel_size = 1.4e-6 the internet suggests 1.4 micron http://www.phonearena.com/news/Samsung-Galaxy-S-III-torn-down-has-same-camera-sensor-as-Apple-iPhone-4S_id30837
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
    
    horizontal_fov = 2*np.arctan(width/2.0/focal_length)
    vertical_fov = 2*np.arctan(height/2.0/focal_length)
    
    return {'horizontal_fov':horizontal_fov, 'vertical_fov':vertical_fov}
    
def calculate_degrees_to_box_edge(fov, altitude):
    R_E = 6371.0e3 #radius is 6,371 kilometers
    #print(altitude, fov['horizontal_fov'], R_E)
    horizontal_degrees = altitude*np.arctan(fov['horizontal_fov']/2.0) * 180.0/(np.pi*R_E)
    vertical_degrees = altitude*np.arctan(fov['vertical_fov']/2.0) * 180.0/(np.pi*R_E)
    
    return {'horizontal_degrees':horizontal_degrees, 'vertical_degrees':vertical_degrees}
    
def create_image_bounds(center, degrees_to_edge, rotation=0.0):
    #need to account for rotation?  Or does GE take the bound and then rotate.
    #Assuming the latter for now
    north = center['latitude'] + degrees_to_edge['vertical_degrees']
    south = center['latitude'] - degrees_to_edge['vertical_degrees']
    east = center['longitude'] + degrees_to_edge['horizontal_degrees']
    west = center['longitude'] - degrees_to_edge['horizontal_degrees']
    
    return {'north':north, 'south':south, 'east':east, 'west':west}
    
#def create_kml(output_name):
#    top = et.Element('kml')
#    ns = {'xmlns' : 'http://www.opengis.net/kml/2.2',
#      'xmlns:gx' : 'http://www.google.com/kml/ext/2.2',
#      'xmlns:kml' : 'http://www.opengis.net/kml/2.2',
#      'xmlns:atom' : 'http://www.w3.org/2005/Atom'}
#    top.attrib = ns
#    
#    return top
#    
#def write_kml(top):
#    et.ElementTree(top).write('ALP.kml', encoding="UTF-8", xml_declaration=None, default_namespace=None, method="xml")
#    
#def add_GroundOverlay(parent, image_name):

mypath = "TimePics_20150718_141536"
#mypath = "kml_test_pics"
image_list = [ f for f in os.listdir(mypath) if os.path.isfile(os.path.join(mypath,f)) ]
print(image_list)

this_kml = alpkml.Kml()

for image in image_list:
    exif_data = get_exif(mypath + "/" + image)
    ugly_gps = exif_gps(exif_data)
    print(ugly_gps)
    if len(ugly_gps) > 3:
        pretty_gps = exif_dms_to_dd(ugly_gps)
        #print(pretty_gps)
        fov = calculate_fov(exif_data)
        degrees_to_edge = calculate_degrees_to_box_edge(fov, 30.0)
        image_bounds = create_image_bounds(pretty_gps, degrees_to_edge, rotation=0.0)
        
        this_kml.add_GroundOverlay(mypath + "/" + image, image_bounds)

this_kml.write('ALP.kml')


#ExifResolutionUnit 1=none, 2=inch, 3=cm
