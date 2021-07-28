package net.etfbl.mdp.zsmdp.main;

import java.awt.Desktop;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.ResourceBundle;

import javax.xml.rpc.ServiceException;

import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.Duration;
import javafx.stage.Stage;
import net.etfbl.mdp.czmdp.soap.UserService;
import net.etfbl.mdp.czmdp.soap.UserServiceServiceLocator;
import net.etfbl.mdp.model.Message;
import net.etfbl.mdp.model.User;
import net.etfbl.mdp.rmi.ReportInterface;

import com.google.gson.Gson;

public class MainPageController implements Initializable {	
	
	@FXML
	public  Label username = new Label();
	@FXML
	public  Label location = new Label();
	@FXML
	public ChoiceBox<String> locations;
	@FXML
	public ChoiceBox<String> activeUsers;
	@FXML
	public Button logOutBtn;
	@FXML
	public Button schedule;
	@FXML
	public TextField messageField;
	@FXML
	public TextArea inbox;
	@FXML
	public Circle messageArrived;
	@FXML
	public ImageView notificationBell;
	@FXML
	public TextArea notificationField;	
	@FXML
	public Button msgBtn;
	
	public static User user;
	private static final int CHAT_PORT = 9999; 
	private boolean sendingFile = false;
	private File fileToSend;
	
	
	public void showOnlineUsers(String city) {
		User[] users = null;
	
		UserServiceServiceLocator locator = new UserServiceServiceLocator();
		try {
			UserService service = locator.getUserService();
			users = service.getOnlineUsers();
			
			User u = service.getActiveUser(city);
			
			activeUsers.getItems().clear();
			if(u != null)
			{
				//activeUsers.getItems().clear();
				activeUsers.getItems().add(u.getUsername());
			}
			
		} catch(Exception e) {
			e.printStackTrace();
		}			
	}
	
	
	
	@Override
	public void initialize(URL arg0, ResourceBundle arg1) {
			
		username.setText(user.getUsername());
		location.setText(user.getCity());		
		
		UserServiceServiceLocator locator = new UserServiceServiceLocator();
		
		try {
			UserService service = locator.getUserService();
			service.registerLogin(user);
			user.setPort(service.assignPort());
			
		} catch (Exception e) {			
			e.printStackTrace();
		}
		
		System.out.println(user);
		
		locations.getItems().add("Banjaluka");
		locations.getItems().add("Bijeljina");
		locations.getItems().add("Sarajevo");
		locations.getItems().add("Mostar");
		locations.getItems().add("Trebinje");
		
		locations.getSelectionModel().select(Arrays.asList(Main.locations).indexOf(user.getCity()));
		activeUsers.getItems().clear();		
		
		checkForNewMessages();
		checkForNotifications();
	
	}
	
	public void locationSelected() {
		String selectedCity = locations.getValue();
		
		showOnlineUsers(selectedCity);
	}
	
	public void logOut() {
		UserServiceServiceLocator locator = new UserServiceServiceLocator();
		try {
			UserService service = locator.getUserService();
			service.registerLogout(user);
			
		} catch(Exception e) {
			e.printStackTrace();
		}
		Stage stage = (Stage)logOutBtn.getScene().getWindow();
		stage.close();
		
	}
	
	public void sendMessage() {
		
		InetAddress addr;
		
		if(locations.getValue()==null || messageField.equals("") || activeUsers.getValue()==null)
		{
			moveButton(msgBtn);
			return;
		}
		
		try {
			
			addr = InetAddress.getByName("localhost");
			Socket socket = new Socket(addr,CHAT_PORT);
			
			//PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),true);
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

			UserServiceServiceLocator locator = new UserServiceServiceLocator();
			UserService service = locator.getUserService();
			
			Message message = new Message(user.getUsername(),messageField.getText(),activeUsers.getValue(),service.getPort(activeUsers.getValue()) );
			
			Gson gson = new Gson();
			String message_string = gson.toJson(message);
			
			out.writeObject(message_string);
	
			String status=(String)in.readObject();
			System.out.println(status);
			
			socket.close();
			out.close();
			in.close();
			messageField.clear();
			
			
		} catch (UnknownHostException e) {			
			e.printStackTrace();
		} catch (IOException e) {			
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (ServiceException e) {			
			e.printStackTrace();
		}		
		
	}
	
	public void sendFile() {
		Stage stage = new Stage();
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("ZSMDP File Chooser");
		
		File fileSelected = fileChooser.showOpenDialog(stage);
		
		if(fileSelected!=null) {
			fileToSend = fileSelected;
			messageField.setText(fileToSend.getName());
			sendingFile = true;
		}
	}
	
	private void checkForNewMessages() {
			
		new Thread(() -> {
			
			System.out.println("Pokrenut tred za provjeru pristiglih poruka na portu " + user.getPort());
			
			
			Gson gson = new Gson();
			
			try {
				ServerSocket ss = new ServerSocket(user.getPort());
				
				while(true) {					
					
				Socket socket = ss.accept();
				System.out.println("Prihvacen klijent " +socket);
				
				ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
				ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
				
				String message_string = (String) ois.readObject();
				
				Message message = gson.fromJson(message_string, Message.class);
				oos.writeObject("Primljenoo");		
				System.out.println("Primljena poruka " +message.getMessage());
								
				messageArrived.setVisible(true);	
				
				String m = "\uD83D\uDCAC" +" " +message.getSenderUsername() + "   \n\t" + message.getMessage() + "\n";
				inbox.appendText(m);
					
	
					
				try {
					Thread.sleep(500);
				} catch (Exception e) {
					e.printStackTrace();
				}					
					
			}
		}
			catch(Exception e) {
				e.printStackTrace();
			}
		}).start();
	}
	
	
	public void messageSeen() {
		messageArrived.setVisible(false);
	}
	
	
	public void sendReport() {
		
		Stage stage = new Stage();
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("ZSMDP Report Chooser");
		fileChooser.getExtensionFilters().add(new ExtensionFilter("PDF Files","*.pdf"));
		File fileSelected = fileChooser.showOpenDialog(stage);
		
		if(fileSelected != null)
		{			
			System.out.println("odabran fajl " + fileSelected.getName());
			
			System.setProperty("java.security.policy", "./resources" + File.separator + "client_policyfile.txt");			
			
			if(System.getSecurityManager() == null ) {
				System.setSecurityManager( new SecurityManager());
			}
			
			
			try {
				byte[] fileContent = Files.readAllBytes(fileSelected.toPath());
				
				String name = "ReportServer";
				Registry registry = LocateRegistry.getRegistry(1099);
				ReportInterface server = (ReportInterface) registry.lookup(name);
				
				System.out.println(user.getUsername() + " poslao izvjestaj " + fileSelected.getName() + " na AZSMDP");
				server.uploadReport(fileSelected.getName(), fileContent, user.getUsername());
				
			} catch (Exception e) {
				e.printStackTrace();
			}
					
		}
	}
	
	public void ringTheBell() {
		
		RotateTransition rotateTransition=new RotateTransition(Duration.millis(200), notificationBell);
		rotateTransition.setFromAngle(30);
		rotateTransition.setToAngle(-30);
		
		rotateTransition.setAutoReverse(true);
		rotateTransition.setCycleCount(3);
		rotateTransition.setInterpolator(Interpolator.EASE_BOTH);
		rotateTransition.play();
		rotateTransition.setOnFinished(e-> {notificationBell.setRotate(0);});		
	}
	
	public void sendNotification() {
		
		Stage primaryStage = new Stage();
		Parent root;
		try {
			root = FXMLLoader.load(getClass().getResource("SendNotification.fxml"));
		
			primaryStage.setTitle("Posalji obavjestenje");
			primaryStage.getIcons().add(new Image(new FileInputStream(new File(Main.resources+File.separator+"icon.png"))));
			primaryStage.setScene(new Scene(root,600,400));
						
			primaryStage.show();
		
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public void checkForNotifications() {
				
		new Thread(()-> {
			MulticastSocket socket = null;
			byte[] buffer = new byte[256];
			
			try {
				socket = new MulticastSocket(SendNotificationController.PORT);
				InetAddress group = InetAddress.getByName(SendNotificationController.HOST);
				socket.joinGroup(group);
				
				while(true) {
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					socket.receive(packet);
					
					String notification = new String(packet.getData(),0, packet.getLength());
					
					notificationField.appendText(notification+"\n");
					
					ringTheBell();
					
					try {
						Thread.sleep(500);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
			} catch (IOException e) {				
				e.printStackTrace();
			}
		}).start();
	}
	
	
	public void showSchedule() {
		
		Stage primaryStage = new Stage();
		Parent root;
		try {
			
			TrainScheduleController.location = user.getCity();
		
			root = FXMLLoader.load(getClass().getResource("TrainSchedule.fxml"));
		
			primaryStage.setTitle("Red voznje");
			primaryStage.getIcons().add(new Image(new FileInputStream(new File(Main.resources+File.separator+"icon.png"))));
			primaryStage.setScene(new Scene(root,600,400));
						
			primaryStage.show();
		
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public void trainPassed() {
		
		Stage primaryStage = new Stage();
		Parent root;
		try {
			
			TrainScheduleController.location = user.getCity();
		
			root = FXMLLoader.load(getClass().getResource("TrainPassed.fxml"));
		
			primaryStage.setTitle("Red voznje");
			primaryStage.getIcons().add(new Image(new FileInputStream(new File(Main.resources+File.separator+"icon.png"))));
			primaryStage.setScene(new Scene(root,600,400));
						
			primaryStage.show();
		
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	private void moveButton(Button btn) {		
		TranslateTransition translateTransition = new TranslateTransition(Duration.millis(200));
		translateTransition.setNode(btn);
		translateTransition.setFromX(-5);
		translateTransition.setToX(5);
		translateTransition.setAutoReverse(true);
		translateTransition.setCycleCount(3);
		translateTransition.setInterpolator(Interpolator.EASE_BOTH);
		translateTransition.play();
		translateTransition.setOnFinished(e -> {btn.setTranslateX(0);});		
	
	}
}
