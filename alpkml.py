from xml.etree import ElementTree as et

class Kml(object):
    def __init__(self):
        self.root = et.Element('kml')
        ns = {'xmlns' : 'http://www.opengis.net/kml/2.2',
        'xmlns:gx' : 'http://www.google.com/kml/ext/2.2',
        'xmlns:kml' : 'http://www.opengis.net/kml/2.2',
        'xmlns:atom' : 'http://www.w3.org/2005/Atom'}
        self.root.attrib = ns
        self.doc = et.SubElement(self.root,'Document')

    def add_Placemark(self, name, desc, lat, lon, alt):
        pm = et.SubElement(self.doc,'Placemark')
        et.SubElement(pm,'name').text = name
        et.SubElement(pm,'description').text = desc
        pt = et.SubElement(pm,'Point')
        et.SubElement(pt,'coordinates').text = '{},{},{}'.format(lat,lon,alt)
        
    def add_GroundOverlay(self, image_file, bounds, rotation=0.0):
        pm = et.SubElement(self.doc,'GroundOverlay')
        #et.SubElement(pm,'name').text = name
        #et.SubElement(pm,'description').text = desc
        image = et.SubElement(pm,'Icon')
        et.SubElement(image,'href').text = image_file
        et.SubElement(image,'viewBoundScale').text = str(0.75)
        box = et.SubElement(pm,'LatLonBox')
        et.SubElement(box,'north').text = str(bounds['north'])
        et.SubElement(box,'south').text = str(bounds['south'])
        et.SubElement(box,'east').text = str(bounds['east'])
        et.SubElement(box,'west').text = str(bounds['west'])
        et.SubElement(box,'rotation').text = rotation
        
    def write(self,filename):
        tree = et.ElementTree(self.root)
        tree.write(filename, encoding="UTF-8", xml_declaration=None, default_namespace=None, method="xml")