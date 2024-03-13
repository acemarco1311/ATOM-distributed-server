import java.io.*; 
import java.util.*; 
import org.w3c.dom.*; 
import javax.xml.transform.*; 
import javax.xml.parsers.*; 
import javax.xml.transform.dom.*; 
import javax.xml.transform.stream.*; 

// this class is using for working with XML Parsing 
public class XMLParser{ 
    private static final int FIRST_ITEM = 0; 
    // constructor
    // input: none
    // output: none
    public XMLParser(){

    }

    //get feed items from the given text file location
    //each feed item is a HashMap
    //input: String: location of the text file 
    //output: ArrayList of HashMap(list of feed items in text)
    public ArrayList<HashMap<String, String>> getFeedItemsFromTextFile(String textFileLocation){
        // init the Array of object to return
        ArrayList<HashMap<String, String>> feedItems = new ArrayList<HashMap<String, String>>(); 
        try{
            // open and read file from the given location
            File inputFile = new File(textFileLocation); 
            if(inputFile.length() == 0){
                return null; 
            }
            Scanner reader = new Scanner(inputFile); 
            // currentIndex is the index of current working HashMap in array
            int currentIndex = 0; 
            // init the first object in array
            HashMap<String, String> initialObject = new HashMap<String, String>(); 
            feedItems.add(initialObject); 
            // read file
            while(reader.hasNextLine()){
                String currentLine = reader.nextLine(); 
                // if got entry, add new object to array
                if(currentLine.equals("entry") == true){
                    HashMap<String, String> object = new HashMap<String, String>(); 
                    feedItems.add(object); 
                    currentIndex++; 
                    continue; 
                }
                // each line contains a pair of key and value, separated by a colon
                String key = ""; 
                String value = ""; 
                int colonIndex = currentLine.indexOf(':'); 
                key = currentLine.substring(0, colonIndex); 
                value = currentLine.substring(colonIndex+1); 
                // add this pair to the current working object 
                HashMap<String, String> currentObject = feedItems.get(currentIndex); 
                currentObject.put(key, value); 
            }
        }
        catch(FileNotFoundException e){
            System.out.println("File not found."); 
            e.printStackTrace(); 
        }
        return feedItems; 
    }

    // this function to parse feed items in an ArrayList<HashMap> to XML Document
    // input: ArrayList of Feed Items, each Feed Items is a HashMap<String, String> 
    // output: XMLDocument
    public Document parseFeedItemListToXMLDocument(ArrayList<HashMap<String, String>> feed){
        if(feed == null){
            return null; 
        }
        try{
            DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance(); 
            DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder(); 
            Document document = documentBuilder.newDocument(); 
            // create the root element named 'feed' 
            Element root = document.createElement("feed"); 
            //add attribute for feed element
            root.setAttribute("xml:lang", "en-US"); 
            root.setAttribute("xmlns", "http://www.w3.org/2005/Atom"); 
            // add the root element
            document.appendChild(root); 
            // loop through each feed item
            for(int i=0; i<feed.size(); i++){
                // create an element 'entry' for each item
                Element entry = document.createElement("entry"); 
                HashMap<String, String> currentItem = feed.get(i); 
                // for each item, add each element (id, title, etc) to the XML Document
                currentItem.forEach((key, value) -> {
                    // if found contentServerID or timestamp 
                    // set those 2 as attributes of entry element 
                    // instead of subElement of inside entry element
                    if(key.equals("contentServerID") == true || key.equals("timestamp") == true ){
                        entry.setAttribute(key, value); 
                    }
                    else{
                        Element subElement = document.createElement(key); 
                        // if the element is 'author', we need to create a sub-element 'name' for the 'author' element
                        if (key.equals("author") == true){
                            Element nameElement = document.createElement("name"); 
                            nameElement.appendChild(document.createTextNode(value)); 
                            subElement.appendChild(nameElement); 
                        }
                        else{
                            subElement.appendChild(document.createTextNode(value)); 
                        }
                        entry.appendChild(subElement); 
                    }
                }); 
                // add this item to doc via the root element 
                root.appendChild(entry); 
            }
            return document; 
        }
        catch(ParserConfigurationException pce){
            System.out.println("XML Parser error."); 
            pce.printStackTrace(); 
        }
        // return null if error appeared
        return null; 
    }

    // this function will parse normal text to XML Document 
    // input: String, input file location
    // output: Document: XML Document Object
    public Document parseNormalTextToXMLDocument(String inputFileLocation){
        ArrayList<HashMap<String, String>> feedItems = this.getFeedItemsFromTextFile(inputFileLocation); 
        Document doc = this.parseFeedItemListToXMLDocument(feedItems); 
        return doc; 
    }


    // this function will parse the feed in XML Document to the Normal Text in the input file 
    // this can be used for printing the aggregated feed to the client, or store the aggregated feed in FeedLog in Aggregation Server
    // Assumption: for any element in a feed, there is only one piece of content, e.g any feed will only have at most 1 title, 1 subtitle, 1 summary, etc. 
    // input: the feed in XML Document
    // output: the feed in normal text (like in the input file)
    public String parseXMLDocumentToNormalText(Document doc){
        if(doc == null){
            return null; 
        }
        // init the output
        String normalText = ""; 
        Element root = doc.getDocumentElement(); 
        // get each feed item
        NodeList feedItems = root.getElementsByTagName("entry"); 
        // loop through feed items
        for(int i = 0; i < feedItems.getLength(); i++){
            Element currentItem = (Element)feedItems.item(i); 
            // get the timestamp and content server id 
            // the normal text in AggregationServer needs to know these two
            String currentItemContentServerID = currentItem.getAttribute("contentServerID"); 
            String currentItemTimestamp = currentItem.getAttribute("timestamp"); 
            // assumption that any element will have only one piece of information, which is why we can use .item(0) to get the content of the element
            Element title = (Element)currentItem.getElementsByTagName("title").item(FIRST_ITEM); 
            Element subtitle = (Element)currentItem.getElementsByTagName("subtitle").item(FIRST_ITEM);
            Element link = (Element)currentItem.getElementsByTagName("link").item(FIRST_ITEM); 
            Element updated = (Element)currentItem.getElementsByTagName("updated").item(FIRST_ITEM); 
            Element author = (Element)currentItem.getElementsByTagName("author").item(FIRST_ITEM); 
            // element name is a sub-element in the author element
            // however some items don't have the author element
            // therefore, we need to check if the author element is null before retrieving the name element
            Element name; 
            if(author != null){
                name = (Element)author.getElementsByTagName("name").item(FIRST_ITEM); 
            }
            else{
                name = null; 
            }
            Element id = (Element)currentItem.getElementsByTagName("id").item(FIRST_ITEM); 
            Element summary = (Element)currentItem.getElementsByTagName("summary").item(FIRST_ITEM); 
            // constructing the output in the format: ElementName:ElementContent (similar to the input file)
            // need to check if a particular element appear in the item because some items will miss some elements (author, subtitle, etc) 
            if(currentItemContentServerID != null && currentItemContentServerID.isEmpty() == false){
                normalText += "contentServerID: " + currentItemContentServerID + "\n"; 
            }
            if(currentItemTimestamp != null && currentItemTimestamp.isEmpty() == false){
                normalText += "timestamp: " + currentItemTimestamp + "\n"; 
            }
            if(title != null && title.getTextContent().isEmpty() == false){
                normalText += "title:" + title.getTextContent() + "\n"; 
            }
            if(subtitle != null && subtitle.getTextContent().isEmpty() == false){
                normalText += "subtitle:" + subtitle.getTextContent() + "\n"; 
            }
            if(link != null && link.getTextContent().isEmpty() == false){
                normalText += "link:" + link.getTextContent() + "\n"; 
            }
            if(author != null && name != null && name.getTextContent().isEmpty() == false){
                normalText += "author:" + name.getTextContent() + "\n"; 
            }
            if(id != null && id.getTextContent().isEmpty() == false){
                normalText += "id:" + id.getTextContent() + "\n"; 
            }
            if(summary != null && summary.getTextContent().isEmpty() == false){
                normalText += "summary:" + summary.getTextContent() + "\n"; 
            }
            // the last item will not have the entry keyword
            if(i != feedItems.getLength() - 1){
                normalText += "entry"; 
                normalText += "\n"; 
            }
        }
        // trim the output to delete addition new lines
        normalText = normalText.trim(); 
        return normalText; 
    }

    // some basic tests
    public static void main(String args[]){
        //close scanner 
        Scanner reader = new Scanner(System.in); 
        reader.close(); 
    }
}
