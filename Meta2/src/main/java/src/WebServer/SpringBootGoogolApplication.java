package src.WebServer;

import java.rmi.Naming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import src.RMIGateway.Configuration;
import src.RMIInterface.RMIGatewayInterface;

@SpringBootApplication
public class SpringBootGoogolApplication
{
	public static void main(String[] args) {
		SpringApplication.run(SpringBootGoogolApplication.class, args);
	}

	/**
	 * Allows the RMIGateway to be accessible to clients through the Web
	 * @return Interface to interact with the Gateway server
	 * @throws Exception
	 */
	@Bean
	public RMIGatewayInterface searchGateway() throws Exception
	{
		RMIGatewayInterface gateway = null;
		boolean connected = false;
		while(!connected)
		{
			try
			{
				gateway = (RMIGatewayInterface) Naming.lookup("rmi://"+ Configuration.IP_GATEWAY +"/gateway");
				connected = true;
			} catch (Exception e) {
				System.out.println("Error connecting to server, retrying in 3 seconds");
				Thread.sleep(3000);
			}
		}

		return gateway;
	}


}
