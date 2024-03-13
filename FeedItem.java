import java.io.*; 
import java.util.*; 
import org.w3c.dom.*; 
import javax.xml.transform.*; 
import javax.xml.parsers.*; 
import javax.xml.transform.dom.*; 
import javax.xml.transform.stream.*; 
import java.time.LocalDateTime; 

public class FeedItem{
    private static final int FIRST_ITEM = 0; 
    private Element item; // XML Element
    private int contentServerID; // which content server id the element came from
    private LocalDateTime timestamp; // the time the server receive this feed item


    // constructor: create a FeedItem object
    // input: Element (from XML DOM Tree), Integer(the content server id which this FeedItem came from), LocalDateTime(the time when the server received this FeedItem); 
    // output: none
    public FeedItem(Element entry, int receivedFrom, LocalDateTime receivedAt){
        if(entry == null || receivedFrom < 0 || receivedAt == null){
            throw new IllegalArgumentException("Invalid arguments for FeedItem"); 
        }
        this.item = entry; 
        this.contentServerID = receivedFrom; 
        this.timestamp = receivedAt; 
        // set new attributes: contentServerID and timestamp for this feedItem element
        this.item.setAttribute("contentServerID", Integer.toString(receivedFrom)); 
        this.item.setAttribute("timestamp", receivedAt.toString()); 
    }

    // this function will check if this FeedItem is a valid FeedItem
    // a valid FeedItem must has: title, link, id
    // input: no
    // output: boolean: true if this is a valid FeedItem, false otherwise
    public boolean isValid(){
        boolean result = true; 
        String title = this.getTitleInString(); 
        String link = this.getLinkInString(); 
        String id = this.getIdInString(); 
        if(title == null){
            result = false; 
        }
        else if(title != null && title.isEmpty() == true){
            result = false; 
        }
        else if(link == null){
            result = false; 
        }
        else if(link != null && link.isEmpty() == true){
            result = false; 
        }
        else if(id == null){
            result = false; 
        }
        else if(id != null && id.isEmpty() == true){
            result = false; 
        }
        return result; 
    }

    // override equals() method to compare 2 FeedItem objects
    // 2 FeedItems are considered equivalent if they came from the same contentServer(has the same contentServerID) and has the same Feed id
    // input: Object (to compare to the current FeedItem); 
    // output: boolean true if 2 FeedItems is equivalent, false otherwise
    @Override
    public boolean equals(Object obj){
        // if the object compare to itself, return true
        if(this == obj){
            return true; 
        }
        // if 2 objects are not the same type, return false
        if(!(obj instanceof FeedItem)){
            return false; 
        }
        boolean result = false; 
        FeedItem newItem = (FeedItem)obj; 
        int firstContentServerID = this.contentServerID; 
        int secondContentServerID = newItem.getContentServerID(); 
        String firstFeedID = this.getIdInString(); 
        String secondFeedID = newItem.getIdInString(); 
        // return true if and only if two FeedItem came from the same Content Server and has the same id
        if(firstContentServerID == secondContentServerID){
            if(firstFeedID != null && secondFeedID != null && firstFeedID.equals(secondFeedID) == true){
                result = true; 
            }
            else if(firstFeedID == null && secondFeedID == null){
                result = true; 
            }
        }
        return result; 
    }

    // override hashCode() method after overriding equals()
    // input: none
    // output: Integer
    @Override 
    public int hashCode(){
        int result = 17; 
        result = 31 * result + this.contentServerID; 
        if(this.item != null && this.getIdInString() != null && this.getIdInString().isEmpty() == false){
            result = 31 * result + this.getIdInString().hashCode(); 
        }
        return result; 
    }

    // get the 'entry' Element of this FeedItem
    // input: none
    // output: Element (which is the 'entry' node in the XMLDocument)
    public Element getItem(){
     return this.item; 
    }

    // get the content server id which send this feed item
    // input: none 
    // output: Integer: content server ID
    public int getContentServerID(){
     return this.contentServerID; 
    }

    // get the timestamp when the aggregation server receive this FeedItem from PUT request
    // input: none; 
    // output: LocalDateTime
    public LocalDateTime getTimestamp(){
     return this.timestamp; 
    }

    // get the title of this FeedItem
    // input: none
    // output: String: the title of this FeedItem, null if this FeedItem doesn't contain any title
    public String getTitleInString(){
     Element title = (Element)this.item.getElementsByTagName("title").item(FIRST_ITEM); 
     String titleInString = null; 
     if(title != null){
         titleInString = title.getTextContent(); 
     }
     return titleInString; 
    }

    // get the subtitle of this FeedItem 
    // input: none
    // output: String: the subtitle of this FeedItem, null if this FeedItem doesn't contain any subtitle
    public String getSubtitleInString(){
     Element subtitle = (Element)this.item.getElementsByTagName("subtitle").item(FIRST_ITEM); 
     String subtitleInString = null; 
     if(subtitle != null){
         subtitleInString = subtitle.getTextContent(); 
     }
     return subtitleInString; 
    }

    // get the link of this FeedItem
    // input: none
    // output: String: the link of this FeedItem, null if this FeedItem doesn't contain any link
    public String getLinkInString(){
     Element link = (Element)this.item.getElementsByTagName("link").item(FIRST_ITEM); 
     String linkInString = null; 
     if(link != null){
         linkInString = link.getTextContent(); 
     }
     return linkInString; 
    }

    // get the updated of this FeedItem
    // input: none
    // output: String: updated of this FeedItem, null if this FeedItem doesn't contain update field
    public String getUpdatedInString(){
     Element updated = (Element)this.item.getElementsByTagName("updated").item(FIRST_ITEM); 
     String updatedInString = null; 
     if(updated != null){
         updatedInString = updated.getTextContent(); 
     }
     return updatedInString; 
    }

    // get author of this FeedItem
    // input: none
    // output: String: the author name of this FeedItem, null if this  FeedItem doesn't contain any author
    public String getAuthorNameInString(){
     Element author = (Element)this.item.getElementsByTagName("author").item(FIRST_ITEM); 
     String authorNameInString = null;
     if(author != null){
         Element authorName = (Element)author.getElementsByTagName("name").item(FIRST_ITEM); 
         if(authorName != null){
             authorNameInString = authorName.getTextContent(); 
         }
     }
     return authorNameInString; 
    }

    // get the id of this FeedItem
    // input: none
    // output: String: the id of the FeedItem, null if this FeedItem doesn't contain id field
    public String getIdInString(){
     Element id = (Element)this.item.getElementsByTagName("id").item(FIRST_ITEM); 
     String idInString = null; 
     if(id != null){
         idInString = id.getTextContent(); 
     }
     return idInString; 
    }

    // get the summary of this FeedItem
    // input: none
    // output: String: the summary of this FeedItem, null if this FeedItem doesn't contain summary
    public String getSummaryInString(){
     Element summary = (Element)this.item.getElementsByTagName("summary").item(FIRST_ITEM); 
     String summaryInString = null; 
     if(summary != null){
         summaryInString = summary.getTextContent(); 
     }
     return summaryInString; 
    }


    public static void main (String args[]){
    }
}
