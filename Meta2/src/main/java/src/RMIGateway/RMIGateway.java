package src.RMIGateway;

import src.RMIInterface.RMIBarrelInterface;
import src.RMIInterface.RMIGatewayInterface;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

/**
 * Class Component that client connects to, in order to use the app functionalities
 */
public class RMIGateway extends UnicastRemoteObject implements RMIGatewayInterface
{
    private AdminPage adminPage;
    private HashMap<String, Integer> relevanceDictionary;

    /**
     * Class Constructor
     * @throws RemoteException
     */
    public RMIGateway() throws RemoteException
    {
        super();
        this.relevanceDictionary = new HashMap<>();
        adminPage = new AdminPage(relevanceDictionary);
    }

    /**
     * Filters the words that start with A-M from client input
     * @param frase Client input
     * @return Words that start with A-M, separated by spaces
     */
    private String separateWordsAlphabet(String frase)
    {
        String[] words = frase.split(" ");
        StringBuilder wordsA_M = new StringBuilder();
        for (String x : words) {
            if (x.toLowerCase().charAt(0) < 'm') {
                wordsA_M.append(x).append(" ");
            }
        }
        return wordsA_M.toString();
    }
    /**
     * Filters the words that start with N-Z from client input
     * @param frase Client input
     * @return Words that start with N-Z, separated by spaces
     */
    private String separateWordsAlphabet2(String frase)
    {
        String[] words = frase.split(" ");
        StringBuilder wordsN_Z = new StringBuilder();
        for (String x : words) {
            if (x.toLowerCase().charAt(0) >= 'm') {
                wordsN_Z.append(x).append(" ");
            }
        }
        return wordsN_Z.toString();
    }

    /**
     * Generates a random barrel ID based on the configured number of barrels IDs that the app is allowed to work with
     * @return Barrel ID
     */
    private int generateRandomID(){ return (int) (Math.random() * Configuration.NUM_BARRELS) + 1;}

    /**
     * Accesses random barrels and retrieves all urls that are connected to a word or a set of words
     * @param word Word or set of words provided by client input
     * @return List of urls related to client input
     * @throws IOException
     */
    @Override
    public List<String> searchWords(String word) throws IOException
    {
        List<String> resultA_M = new ArrayList<String>();
        List<String> resultN_Z = new ArrayList<String>();
        RMIBarrelInterface barrel;
        String wordsA_M = "";
        String wordsN_Z = "";
        long start;
        long finish;

        // Separates the user input into different parts (if there are multiple)
        try {
            wordsA_M = separateWordsAlphabet(word);
            wordsN_Z = separateWordsAlphabet2(word);
        } catch (Exception e) {
            System.out.println("Error separating words alphabetically.");
        }

        int miss_counter = 0;
        if (!wordsA_M.isEmpty())
        {
            int randomBarrel = generateRandomID();
            boolean connected = false;

            while (!connected)
            {
                // If the random access still can't find a working barrel, sends a Remote Exception
                if(miss_counter == Configuration.NUM_MISSES)
                {
                    throw new RemoteException();
                }
                try
                {
                    barrel = (RMIBarrelInterface) Naming
                            .lookup("rmi://"+adminPage.getBarrelIP(randomBarrel) +"/Barrel" + randomBarrel);
                    start = System.currentTimeMillis();
                    resultA_M = barrel.searchWords(wordsA_M);
                    finish = System.currentTimeMillis();
                    adminPage.updateResponseTimesBarrel(randomBarrel,finish-start);
                    connected = true;
                }
                catch (Exception e)
                {
                    // Communicate with Admin Page and notify that this barrel is Offline
                    adminPage.updateOfflineBarrels(randomBarrel);
                    miss_counter += 1;
                    randomBarrel = generateRandomID();
                }
            }
        }
        miss_counter = 0;
        if (!wordsN_Z.isEmpty()) {
            int randomBarrel = generateRandomID();
            boolean connected = false;

            while (!connected)
            {
                if(miss_counter == Configuration.NUM_MISSES)
                {
                    throw new RemoteException();
                }
                try
                {
                    barrel = (RMIBarrelInterface) Naming
                            .lookup("rmi://"+adminPage.getBarrelIP(randomBarrel) +"/Barrel" + randomBarrel);
                    start = System.currentTimeMillis();
                    resultN_Z = barrel.searchWords(wordsN_Z);
                    finish = System.currentTimeMillis();
                    adminPage.updateResponseTimesBarrel(randomBarrel,finish-start);
                    connected = true;
                }
                catch (Exception e)
                {
                    // Communicate with Admin Page and notify that this barrel is Offline
                    adminPage.updateOfflineBarrels(randomBarrel);
                    miss_counter += 1;
                    randomBarrel = generateRandomID();
                }
            }
        }
        List<String> result = new ArrayList<String>();

        // Compares findings in each part of the user input and stacks all the results in a single list
        if (resultA_M.isEmpty()) result = resultN_Z;
        else if (resultN_Z.isEmpty()) result = resultA_M;

        else
        {
            for (String s : resultA_M)
            {
                if (resultN_Z.contains(s))
                {
                    result.add(s);
                }
            }
        }

        // Increments the number of times the word has been searched, making it more relevant
        if (this.relevanceDictionary.containsKey(word)) {
            this.relevanceDictionary.put(word, this.relevanceDictionary.get(word) + 1);
        } else {
            this.relevanceDictionary.put(word, 1);
        }
        sortRelevanceDictionary();
        return result;

    }

    /**
     * Makes sure the dictionary about most relevant searches is in order
     */
    private void sortRelevanceDictionary()
    {
        // Sort the search dictionary by the number of times a word has been searched
        List<Map.Entry<String, Integer>> list = new LinkedList<Map.Entry<String, Integer>>(
                this.relevanceDictionary.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return (o2.getValue()).compareTo(o1.getValue());
            }
        });

        HashMap<String, Integer> temp = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        this.relevanceDictionary = temp;
        adminPage.updateHashMap(temp);
    }

    /**
     * Accesses random barrels and retrieves all hyperlinks that point to a webpage
     * @param webpage Webpage input from a client
     * @return List of webpages that link to the client input
     * @throws FileNotFoundException
     * @throws IOException
     * @throws NotBoundException
     */
    @Override
    public List<String> searchLinks(String webpage) throws FileNotFoundException, IOException, NotBoundException
    {
        int randomBarrel = generateRandomID();
        int miss_counter = 0;
        RMIBarrelInterface barrel = null;
        List<String> result = null;
        boolean connected = false;
        long start,finish;

        while (!connected)
        {
            if(miss_counter == Configuration.NUM_MISSES)
            {
                throw new RemoteException();
            }
            try
            {
                barrel = (RMIBarrelInterface) Naming.lookup("rmi://"+adminPage.getBarrelIP(randomBarrel) +"/Barrel" + randomBarrel);
                start = System.currentTimeMillis();
                result = barrel.searchLinks(webpage);
                finish = System.currentTimeMillis();
                adminPage.updateResponseTimesBarrel(randomBarrel,finish-start);
                connected = true;
            }
            catch (Exception e)
            {
                // Try again with another barrel
                adminPage.updateOfflineBarrels(randomBarrel);
                miss_counter += 1;
                randomBarrel = generateRandomID();
            }
        }

        return result;
    }

    /**
     * Adds an URL into the Queue in order to be handled by Downloaders and Barrels
     * @param url Client url input
     * @throws RemoteException
     * @throws IOException
     * @throws NotBoundException
     */
    @Override
    public void indexNewURL(String url) throws RemoteException, IOException, NotBoundException
    {
        // Send the url to the urlqueue and the downloader will take care of the rest
        // via tcp
        Socket socket = new Socket("localhost", Configuration.RECEIVE_PORT);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        out.println(url);
        out.close();
        socket.close();
    }



    public static void main(String[] args) throws IOException, NotBoundException
    {
        RMIGateway gateway = new RMIGateway();
        LocateRegistry.createRegistry(1099);
        Naming.rebind("gateway", gateway);

        // Starts up the Downloaders
        for (int i = 1; i <= Configuration.NUM_DOWNLOADERS; i++)
        {
            Downloader d = new Downloader(i);
            try {
                d.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        gateway.adminPage = new AdminPage(gateway.relevanceDictionary);
        gateway.adminPage.showMenu();
    }

    /**
     * Gets the admin interface menu that can be analysed by the client
     * @return Admin page menu as a String
     * @throws RemoteException
     */
    @Override
    public String getAdminMenu() throws RemoteException {
        return adminPage.getStringMenu();
    }


}