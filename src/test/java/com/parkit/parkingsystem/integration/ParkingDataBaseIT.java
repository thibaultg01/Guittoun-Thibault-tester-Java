package com.parkit.parkingsystem.integration;

import com.parkit.parkingsystem.constants.DBConstants;
import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.FareCalculatorService;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Date;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ParkingDataBaseIT {

	private static final Logger logger = LogManager.getLogger("TicketDAO");
	private static DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
	private static ParkingSpotDAO parkingSpotDAO;
	private static TicketDAO ticketDAO;
	private static DataBasePrepareService dataBasePrepareService;
	private static FareCalculatorService fareCalculatorService = new FareCalculatorService();

	@Mock
	private static InputReaderUtil inputReaderUtil;

	@BeforeAll
	private static void setUp() throws Exception {
		parkingSpotDAO = new ParkingSpotDAO();
		parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;
		ticketDAO = new TicketDAO();
		ticketDAO.dataBaseConfig = dataBaseTestConfig;
		dataBasePrepareService = new DataBasePrepareService();
	}

	@BeforeEach
	private void setUpPerTest() throws Exception {
		when(inputReaderUtil.readSelection()).thenReturn(1);
		when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
		dataBasePrepareService.clearDataBaseEntries();
	}

	@AfterAll
	private static void tearDown() {

	}
	
	/**
	 * Integration test for the processIncomingVehicle() method of the ParkingService class.
	 * It check that a ticket is actualy saved in DB and Parking table is updated with availability.
	 *
	 * This test simulates a full parking flow for a car:
	 * - The service processes the incoming vehicle.
	 * - A ticket is created and stored in the database.
	 * - The associated parking spot is marked as unavailable.
	 *
	 * It verifies that:
	 * - A ticket is created and contains the correct vehicle registration number.
	 * - A valid ParkingSpot is associated with the ticket.
	 * - The parking spot is updated to reflect that it is no longer available.
	 */
	@Test
	public void testParkingACar() {
		ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
		
		parkingService.processIncomingVehicle();
		
		// Retrieve the ticket generated for the vehicle "ABCDEF"
	    Ticket ticket = ticketDAO.getTicket("ABCDEF");

	    // Check that the ticket was properly created and is not null
	    assertNotNull(ticket);

	    // Check that the vehicle registration number in the ticket is correct
	    assertEquals("ABCDEF", ticket.getVehicleRegNumber());

	    // Retrieve the associated parking spot from the ticket
	    ParkingSpot parkingSpot = ticket.getParkingSpot();

	    // Check that the parking spot is correctly associated
	    assertNotNull(parkingSpot);

	    // Check that the parking spot is now marked as unavailable
	    assertFalse(parkingSpot.isAvailable());
	}

	/**
	 * Integration test for the processExitingVehicle() method of the ParkingService class.
	 * Check that the fare generated and out time are populated correctly in the database
	 *
	 * This test simulates a complete parking cycle:
	 * - A car enters the parking lot via testParkingACar().
	 * - After a short wait, the car exits the parking lot.
	 * 
	 * It verifies that:
	 * - The vehicle's out-time is correctly recorded in the ticket.
	 * - The parking fare is calculated (expected must be 0.0).
	 *
	 * @throws InterruptedException if the thread sleep is interrupted
	 */
	@Test
	public void testParkingLotExit() throws InterruptedException {
		testParkingACar();
		
		ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
		// TODO: check that the fare generated and out time are populated correctly in the database
		// Wait for 1 second to simulate a short parking duration
	    Thread.sleep(1000);

	    // Process the vehicle exiting the parking lot
	    parkingService.processExitingVehicle();

	    // Retrieve the updated ticket for vehicle "ABCDEF"
	    Ticket ticket = ticketDAO.getTicket("ABCDEF");

	    // Check that the out time was properly recorded
	    assertNotNull(ticket.getOutTime());

	    // Check that the fare is 0.0 since the parking duration is under 30 minutes (free parking)
	    assertEquals(0.0, ticket.getPrice());
	}
	
	/**
	 * Integration test for test the calculation of the price of a ticket via the call of processIncomingVehicle
	 * and processExitingVehicle in the case of a recurring user.
	 *
	 * This test simulates a full parking cycle for a vehicle that has parked more than once.
	 * It performs the following:
	 * - Processes an initial entry and exit (first parking session).
	 * - Processes a second entry and updates the database manually to simulate historical tickets.
	 * - Checks that a 5% discount is applied during the second exit as the vehicle is considered a recurring user.
	 *
	 * It verifies that:
	 * - The parking fare includes the 5% discount for returning users.
	 * - The recorded fare is correctly rounded and matches the expected discounted fare.
	 *
	 * @throws InterruptedException if the thread sleep is interrupted
	 */
	@Test
	public void testParkingLotExitRecurringUser() throws InterruptedException {
		// First simulate an initial entry and exit for the vehicle (first parking session)
	    testParkingLotExit();

	    // Create a new instance of ParkingService
	    ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
	    
	    // Simulate a second entry for the same vehicle
	    parkingService.processIncomingVehicle();

	    // Set up manual timestamps to simulate parking history in the DB
	    Timestamp timestampinTimeTicket1 = new Timestamp(System.currentTimeMillis() - (24 * 60 * 60 * 1000)); // 24h ago
	    Timestamp timestampoutTimeTicket1 = new Timestamp(System.currentTimeMillis() - (2 * 60 * 60 * 1000)); // 2h ago
	    Timestamp timestampinTimeTicket2 = new Timestamp(System.currentTimeMillis() - (60 * 60 * 1000)); // 1h ago

	    Connection con = null;
	    try {
	        // Connect to test database to manually update ticket data
	        con = dataBaseTestConfig.getConnection();

	        // Update first ticket (ID = 1) to set realistic in/out times
	        PreparedStatement ps1 = con.prepareStatement(
	            "update ticket set IN_TIME=?, OUT_TIME=? where VEHICLE_REG_NUMBER='ABCDEF' AND ID=1");
	        ps1.setTimestamp(1, new Timestamp(timestampinTimeTicket1.getTime()));
	        ps1.setTimestamp(2, new Timestamp(timestampoutTimeTicket1.getTime()));
	        ps1.execute();

	        // Update second ticket (ID = 2) to set realistic in-time (ongoing parking)
	        PreparedStatement ps2 = con.prepareStatement(
	            "update ticket set IN_TIME=? where VEHICLE_REG_NUMBER='ABCDEF' AND ID=2");
	        ps2.setTimestamp(1, new Timestamp(timestampinTimeTicket2.getTime()));
	        ps2.execute();

	        // Process exit for the second ticket (should apply discount)
	        parkingService.processExitingVehicle();
	    } catch (Exception ex) {
	        // Log any exception during database interaction
	        logger.error("Error saving ticket info", ex);
	    } finally {
	        // Ensure DB connection is closed
	        dataBaseTestConfig.closeConnection(con);
	    }

	    // Expected fare after 5% discount
	    double value1 = Fare.CAR_RATE_PER_HOUR * 0.95;
	    BigDecimal bd1 = new BigDecimal(value1);
	    bd1 = bd1.setScale(1, RoundingMode.HALF_UP); // Round to 1 decimal place
	    double arround1 = bd1.doubleValue();

	    // Actual fare recorded in the ticket from DB
	    double value2 = ticketDAO.getTicket("ABCDEF").getPrice();
	    BigDecimal bd2 = new BigDecimal(value2);
	    bd2 = bd2.setScale(1, RoundingMode.HALF_UP); // Round to 1 decimal place
	    double arround2 = bd2.doubleValue();

	    // Compare expected and actual fare values with rounding applied
	    assertEquals(arround1, arround2);
	}
}
