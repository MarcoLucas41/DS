package src.RMIInterface;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface RMIGatewayInterface extends Remote
{
    /**
     * Accesses RMIBarrel's storage and retrieves all urls that are connected to a word or a set of words
     * @param words Word or set of words provided by client input
     * @return List of urls related to the client input
     * @throws NotBoundException
     * @throws IOException
     */
    // Accesses RMIBarrel's storage and retrieves all urls that are connected to a word or a set of words
    List<String> searchWords(String words) throws NotBoundException, IOException;

    /**
     * Accesses RMIBarrel's storage and retrieves all hyperlinks that point to a webpage
     * @param webpage Webpage input from a client
     * @return List of webpages related to the client input
     * @throws IOException
     * @throws NotBoundException
     */
    // Accesses RMIBarrel's storage and retrieves all hyperlinks that point to a webpage
    List<String> searchLinks(String webpage) throws IOException, NotBoundException;

    /**
     * Gets the admin interface menu that can be analysed by the client
     * @return Admin page menu as a String
     * @throws RemoteException
     */
    String getAdminMenu() throws RemoteException;

    /**
     * Adds an URL into the Queue in order to be handled by Downloaders and Barrels
     * @param url Client url input
     * @throws IOException
     * @throws NotBoundException
     */
    void indexNewURL(String url) throws IOException, NotBoundException;

}