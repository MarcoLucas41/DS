package src.RMIClient;

import src.RMIGateway.Configuration;
import src.RMIInterface.RMIGatewayInterface;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * Class that runs a client program
 */
public class RMIClient
{
    /**
     * Running function that handles client commands
     * @param gateway
     * @throws Exception
     */
    private void run(RMIGatewayInterface gateway) throws Exception
    {
        printMenu();
        // Create a timer
        Timer timer = new Timer();
        System.out.print("Type in new command: ");
        Scanner scanner = new Scanner(System.in);

        boolean exit = false;

        while (true) {
            int command;
            try {
                command = scanner.nextInt();
            } catch (InputMismatchException e) {
                System.out.println("--------------------------------------------------------------------------");
                invalid_prompt();
                scanner.next();
                continue;
            }
            switch (command)
            {
                case 0:
                    timer.cancel();
                    printMenu();
                    regular_messages();
                    break;
                case 1:
                    timer.cancel();
                    System.out.println("--------------------------------------------------------------------------");
                    System.out.print("Insert URL to webpage: ");
                    scanner.nextLine();
                    String url = scanner.nextLine();
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        System.err.println("Invalid URL (must have <http://> or <https://> prefixes)");
                        regular_messages();
                        break;
                    }
                    gateway.indexNewURL(url);
                    System.out.println("URL has been indexed to queue");
                    regular_messages();
                    break;
                case 2:
                    timer.cancel();
                    System.out.println("--------------------------------------------------------------------------");
                    searchWord(gateway, scanner);
                    break;
                case 3:
                    timer.cancel();
                    System.out.println("--------------------------------------------------------------------------");
                    searchLink(gateway, scanner);
                    regular_messages();
                    break;
                case 4:
                    System.out.println("--------------------------------------------------------------------------");
                    // Define the task to be executed repeatedly
                    TimerTask task = new TimerTask() {
                        @Override
                        public void run()
                        {
                            // Print and flush content at a fixed rate
                            try {
                            System.out.print("\033[H\033[2J");
                            System.out.flush();
                            System.out.println(gateway.getAdminMenu());
                            } catch (RemoteException e) {
                                throw new RuntimeException(e);
                        }
                        }
                    };

                    // Schedule the task to run repeatedly every fixed interval (e.g., 1000 milliseconds = 1 second)
                    timer.scheduleAtFixedRate(task, 0, 1000);
                    //regular_messages();
                    break;
                case 5:
                    timer.cancel();
                    exit = true;
                    break;
                default:
                    timer.cancel();
                    System.out.println("--------------------------------------------------------------------------");
                    invalid_prompt();
                    break;
            }
            if (exit)
            {
                timer.cancel();
                break;
            }
        }
        scanner.close();
    }

    /**
     * Prints warning prompts
     */
    private void invalid_prompt()
    {
        System.err.println("Invalid prompt");
        System.out.print("Type in new command: ");
    }
    /**
     * Prints auxiliary prompts
     */
    private void regular_messages()
    {
        System.out.println("Type '0' to display menu again");
        System.out.print("Type in new command: ");
    }
    /**
     * Makes sure that the client input doesn't contain invalid characters
     */
    private boolean checkSearchFormat(String query) {
        String[] words = query.split(" ");
        for (String word : words) {
            if (word.matches("[^a-zA-Z0-9\\p{L};]+") || word.contains(";")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets all urls that are connected to a word or a set of words
     * @param gateway RMIGateway interface connection
     * @param scanner Scanner for client input
     * @throws NotBoundException
     * @throws IOException
     */
    private void searchWord(RMIGatewayInterface gateway, Scanner scanner)
            throws  NotBoundException,IOException {

        System.out.print("Type in terms for search: ");
        scanner.nextLine();
        String words = scanner.nextLine();

        if (!checkSearchFormat(words)) {
            System.err.println("Invalid search format!");
            System.out.println("--------------------------------------------------------------------------");
            regular_messages();
            return;
        }

        int pageNumber = 1;
        List<String> resultList = gateway.searchWords(words);

        int i = 0;
        while (true)
        {
            System.out.println("--------------------------------------------------------------------------");
            if (resultList.isEmpty() && pageNumber == 1) {
                System.out.println("No results found");
                System.out.println("--------------------------------------------------------------------------");
                regular_messages();
                break;
            } else if (resultList.isEmpty()) {
                System.out.println("No more results found");
                System.out.println("--------------------------------------------------------------------------");
                regular_messages();
                break;
            }

            boolean info = false;
            System.out.println("Search results:");
            for (int j = i; j < resultList.size(); j++) {
                if (j == pageNumber * 10)
                    break;
                String fields[] = resultList.get(j).split(";");
                System.out.println("Link: " + fields[0]);
                System.out.println("Title: " + fields[1]);
                System.out.println("Description: " + fields[2] + "\n");
                info = true;
                i++;
            }

            if (!info || i % 10 != 0)
            {
                System.out.println("No more results");
                System.out.println("--------------------------------------------------------------------------");
                regular_messages();
                return;
            }

            System.out.println("--------------------------------------------------------------------------");
            System.out.println("Next page? (y/n)");
            String next = scanner.nextLine();
            if (next.equals("y"))
                pageNumber++;
            else {
                System.out.println("--------------------------------------------------------------------------");
                regular_messages();
                return;
            }
        }
    }

    /**
     * Gets all hyperlinks that point to a webpage
     * @param gateway RMIGateway interface connection
     * @param scanner Scanner for client input
     * @throws FileNotFoundException
     * @throws IOException
     * @throws NotBoundException
     */
    private void searchLink(RMIGatewayInterface gateway, Scanner scanner)
            throws FileNotFoundException, IOException, NotBoundException {

        System.out.print("Insert hyperlink to webpage: ");
        scanner.nextLine();
        String url = scanner.nextLine();
        List<String> links = (gateway.searchLinks(url));

        if (links.isEmpty()) {
            System.out.println("No results found");
        } else {
            System.out.println("Search results:");
            for (String link : links) {
                System.out.println(link);
            }
        }

    }

    public static void main(String[] args) throws Exception
    {
        boolean connected = false;
        RMIGatewayInterface gateway = null;
        while (!connected)
        {
            try
            {
                gateway = (RMIGatewayInterface) Naming.lookup("rmi://"+ Configuration.IP_GATEWAY +"/gateway");
                connected = true;
            }
            catch (Exception e)
            {
                System.err.println("Error connecting to server, retrying in 3 seconds");
                Thread.sleep(3000);
            }
        }

        RMIClient client = new RMIClient();
        try
        {
            client.run(gateway);
        }
        catch (Exception e) {
            System.err.println("Error connecting to server, retrying in 3 seconds");
            Thread.sleep(3000);
            main(args);
        }

    }

    /**
     * Prints client menu
     */
    private void printMenu() {
        System.out.println("---------------------------------MENU-------------------------------------");
        System.out.println("0 - Refresh menu");
        System.out.println("1 - Index a new URL");
        System.out.println("2 - Search webpages that contain certain terms");
        System.out.println("3 - Consult lists of hyperlinks that are oonnected to a certain webpage");
        System.out.println("4 - Open real-time admin page");
        System.out.println("5 - Exit");
        System.out.println("--------------------------------------------------------------------------");
    }

}