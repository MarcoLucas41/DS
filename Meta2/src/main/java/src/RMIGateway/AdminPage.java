package src.RMIGateway;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.HashMap;

public class AdminPage
{
    private ArrayList<String> downloadersStatus;
    private ArrayList<String> barrelsStatus;
    private ArrayList<String> barrelsIPs;
    private HashMap<String, Integer> relevanceDictionary;
    private HashMap<Integer,ArrayList<Long>> avgTimesBarrels;

    private String stringMenu;

    /**
     * Class Constructor
     * @param relevanceDictionary Dictionary that stores info about most relevant searches by clients
     */
    public AdminPage(HashMap<String, Integer> relevanceDictionary)
    {
        this.downloadersStatus = new ArrayList<String>();
        this.barrelsStatus = new ArrayList<String>();
        this.barrelsIPs = new ArrayList<>();
        this.avgTimesBarrels = new HashMap<>();
        this.relevanceDictionary = relevanceDictionary;
    }

    /**
     * Calls functions that will prepare the menu for client analysis
     */
    public void showMenu()
    {
        initializeDataStructures();
        stringMenu = printAdminPage();
        getActiveDownloadersAndBarrels();
    }

    /** Retrieves the IP address of the machine that is running the Barrel with id as parameter
     * @param id Barrel ID
     * @return Barrel IP address
     */
    public String getBarrelIP(int id)
    {
        return this.barrelsIPs.get(id-1);
    }

    /**
     * Multicast setup for receiving messages from Downloaders or Barrels
     */
    private void getActiveDownloadersAndBarrels()
    {
        MulticastSocket socket = null;
        try
        {
            socket = new MulticastSocket(Configuration.MULTICAST_PORT);
            InetAddress group = InetAddress.getByName(Configuration.MULTICAST_ADDRESS);
            socket.joinGroup(group);

            while (true)
            {
                byte[] buffer = new byte[16384];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength());
                updateStatus(msg);
                    stringMenu = printAdminPage();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            socket.close();
        }
    }

    /**
     * Updates the status of a Downloader or Barrel
     * @param msg Multicast message from Downloader or Barrel
     */
    private void updateStatus(String msg)
    {
        String[] msg_split = msg.split(";");

        // Protocol : "type | Downloader; status | Active; url | www.example.com;

        if (msg_split[0].split("\\|")[1].trim().equals("Downloader"))
        {
            int index = Integer.parseInt(msg_split[1].split("\\|")[1].trim());
            String status = msg_split[2].split("\\|")[1].trim();
            String url = msg_split[3].split("\\|")[1].trim();
            this.downloadersStatus.set(index - 1, status + " - " + url);
        }
        else if (msg_split[0].split("\\|")[1].trim().equals("Barrel"))
        {
            int index = Integer.parseInt(msg_split[1].split("\\|")[1].trim());
            String status = msg_split[2].split("\\|")[1].trim();
            String ip = msg_split[3].split("\\|")[1].trim();
            this.barrelsIPs.set(index-1,ip);
            try
            {
                this.barrelsStatus.set(index - 1, status + " - " + ip);
                System.out.println(this.barrelsStatus.get(index-1));
            }
            catch (IndexOutOfBoundsException e)
            {
                System.err.println("Barrel attempting connection with invalid ID");
            }

        }
    }

    /**
     * If the app detects an Exception when trying to access a given barrel, it updates the state of the barrel as Offline
     * @param id Barrel ID
     */
    public void updateOfflineBarrels(int id)
    {
        try
        {
            if(!this.barrelsStatus.get(id-1).equals("Offline"))
            {
                this.barrelsStatus.set(id-1,"Offline");
                this.barrelsIPs.set(id-1,"Null");
                    stringMenu = printAdminPage();
            }
        }
        catch (IndexOutOfBoundsException e)
        {
            System.err.println("Barrel is no longer available or is invalid");
        }
    }

    /**
     * Updates hashmap that stores a new response time for a certain barrel
     * @param id Barrel ID
     * @param time New response time
     */
    public void updateResponseTimesBarrel(int id,long time)
    {
        try
        {
            ArrayList<Long> times = this.avgTimesBarrels.get(id);
            times.add(time);
            this.avgTimesBarrels.replace(id,times);
                stringMenu = printAdminPage();
        }
        catch (IndexOutOfBoundsException e)
        {
            System.err.println("Barrel is invalid");
        }
    }

    /**
     * Returns average of responses time of searches from a given barrel
     * @param times A list of search times inside a barrel
     * @return Average of values obtained from the list given as param
     */
    // Returns average of responses time of searches from a given barrel
    public float getAvgResponseTime(ArrayList<Long> times)
    {
        if(!times.isEmpty())
        {
            float avg = 0;
            for (Long entry : times) {
                avg += entry;
            }
            return (float) (avg/times.size());
        }
        return 0;
        
    }


    /**
     * Builds inside a string the menu from which a client can analyse data stats about the application components
     * @return Admin page in string format to be analysed by client
     */
    private String printAdminPage()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("------- Downloaders -------\n");
        for (int i = 0; i < Configuration.NUM_DOWNLOADERS; i++) {
            int aux = i + 1;
            sb.append("Downloader[" + aux + "] " + this.downloadersStatus.get(i) + "\n");
        }

        sb.append("\n------- Barrels -------\n");
        for (int i = 0; i < Configuration.NUM_BARRELS; i++)
        {
            int aux = i + 1;
            ArrayList<Long> times = this.avgTimesBarrels.get(aux);
            float avg = getAvgResponseTime(times);
            sb.append("Barrel[" + aux + "] " + this.barrelsStatus.get(i) + "\n------Average Response Time : " + avg + " ms\n");
        }

        sb.append("\n------- Most Frequent Searches -------\n");

        if (!this.relevanceDictionary.isEmpty())
        {
            for (int i = 0; i < 10 && i < this.relevanceDictionary.size(); i++) {
                int aux = i + 1;
                if (this.relevanceDictionary.containsKey(this.relevanceDictionary.keySet().toArray()[i])) {
                    sb.append("Search[" + aux + "]: " + this.relevanceDictionary.keySet().toArray()[i] + " - "
                            + this.relevanceDictionary.get(this.relevanceDictionary.keySet().toArray()[i])
                            + "\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Initializes hashmaps with default values
     */
    private void initializeDataStructures()
    {
        for (int i = 0; i < Configuration.NUM_DOWNLOADERS; i++)
        {
            this.downloadersStatus.add("Waiting");
        }
        for (int i = 0; i < Configuration.NUM_BARRELS; i++)
        {
            this.barrelsStatus.add("Offline");
            this.barrelsIPs.add("Null");
            ArrayList<Long> times = new ArrayList<>();
            times.add(0L);
            this.avgTimesBarrels.put(i+1,times);
        }
    }

    /**
     * Updates relevance dictionary
     * @param dic New version of relevance dictionary
     */
    public void updateHashMap(HashMap<String, Integer> dic)
    {
        this.relevanceDictionary = dic;
            stringMenu = printAdminPage();
    }

    /**
     * Gets this instance of Admin Page' version of menu
     * @return Admin page menu in string format
     */
    public String getStringMenu() {
        return this.stringMenu;
    }


}