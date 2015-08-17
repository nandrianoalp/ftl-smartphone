from __future__ import print_function
import os
import alpkml
import image_functions as imfun

"""
Stuff we need:
an assumed elevation and a path to the images.  Could write a function
to check for any folder with images, or use a recent folder.  If we demo
this live, that might be a little better.
"""
#an altitude
assumed_altitude = 30.0 #in meters

#paths used during testing
#mypath = "TimePics_20150718_141536"
mypath = "TimePics_20150811_040300"
#mypath = "kml_test_pics"

#construct the list of images in the path
#make sure to ignore the .txt file with sensor data
image_list = [ f for f in os.listdir(mypath) if (os.path.isfile(os.path.join(mypath,f)) and ".txt" not in f) ]

#create a dictionary of rotations for each image based on sensor data
rotations = imfun.create_rotations_dict(mypath + "/sensorData.txt")

#instantiate a kml object
this_kml = alpkml.Kml()

#loop over images in the directory
for image in image_list:
    #get the exit data for the image
    exif_data = imfun.get_exif(mypath + "/" + image)
    #try to work on the image.  May fail for a number of reasons
    #for example, gps data not embedded.  if fail, we skip the image by passing
    #in the except statment
    try:
        #get the raw gps
        ugly_gps = imfun.exif_gps(exif_data)
        #a check to make sure gps is fully populated.  I don;t remeber why I
        #did this.  may be redundant with above try
        if len(ugly_gps) > 3:
            #convert from minute and second to degrees
            pretty_gps = imfun.exif_dms_to_dd(ugly_gps)
            #calculate a field of view based on sensor and focal length
            fov = imfun.calculate_fov(exif_data)
            #use fov and altitude (assume flat earth) to calculate box bounds
            degrees_to_edge = imfun.calculate_degrees_to_box_edge(fov, assumed_altitude)
            image_bounds = imfun.create_image_bounds(pretty_gps, degrees_to_edge) 
            #add a ground overlay tag and data to kml for this image
            print("Add Image Overlay")
            this_kml.add_GroundOverlay(mypath + "/" + image, image_bounds, rotation=rotations[image])
    except:
        pass

#write the kml
print("Writing KML")
this_kml.write('ALP.kml')

#ExifResolutionUnit 1=none, 2=inch, 3=cm
