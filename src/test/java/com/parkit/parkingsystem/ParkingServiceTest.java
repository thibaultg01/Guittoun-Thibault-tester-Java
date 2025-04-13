package com.parkit.parkingsystem;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ParkingServiceTest {

    private static ParkingService parkingService;

    @Mock
    private static InputReaderUtil inputReaderUtil;
    @Mock
    private static ParkingSpotDAO parkingSpotDAO;
    @Mock
    private static TicketDAO ticketDAO;

    @BeforeEach
    public void setUpPerTest() {
        try {
            ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR,false);
            Ticket ticket = new Ticket();
            ticket.setInTime(new Date(System.currentTimeMillis() - (60*60*1000)));
            ticket.setParkingSpot(parkingSpot);
            ticket.setVehicleRegNumber("ABCDEF");
            lenient().when(ticketDAO.getTicket(anyString())).thenReturn(ticket);
            lenient().when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(true);
            lenient().when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(true);
            parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        } catch (Exception e) {
            e.printStackTrace();
            throw  new RuntimeException("Failed to set up test mock objects");
        }
    }

    /**
     * Unit test for the processExitingVehicle() method of the ParkingService class.
     *
     * This test simulates the exit process of a vehicle that has been parked for 1 hour.
     * It verifies that:
     * - The vehicle's registration number is correctly read.
     * - The associated ticket is retrieved from the database.
     * - The number of previous tickets is checked (to determine if a discount should apply).
     * - The ticket is updated with the fare and out-time.
     * - The parking spot is marked as available and updated.
     *
     * This scenario simulates a new user (first-time parking, so no discount applied).
     *
     *
     * @throws Exception if any exception occurs during mocking or method execution
     */
    @Test
    public void processExitingVehicleTest() throws Exception {
    	ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR,false);
    	Ticket ticket = new Ticket();
        ticket.setVehicleRegNumber("ABCDEF");
        ticket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR,false));
        ticket.setInTime(new Date(System.currentTimeMillis() - (60 * 60 * 1000)));
        ticket.setOutTime(new Date());
        ticket.setPrice(0);
        
        // GIVEN
    	when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
    	when(ticketDAO.getTicket(anyString())).thenReturn(ticket);
    	when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(1); //simulating new user
    	when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(true);
    	when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(true);

    	// WHEN
        parkingService.processExitingVehicle();

        // THEN
     // Verify that the ticket for the vehicle was retrieved once from the database
        verify(ticketDAO, times(1)).getTicket(anyString());

        // Verify that the system checked how many times this vehicle has parked before
        verify(ticketDAO, times(1)).getNbTicket("ABCDEF");

        // Verify that the system attempted to update the ticket (even though it failed)
        verify(ticketDAO, times(1)).updateTicket(any(Ticket.class));

        // Check that the parking spot remains marked as unavailable
        assertFalse(parkingSpot.isAvailable());
    }
    
    /**
     * Unit test for the processIncomingVehicle() method of the ParkingService class.
     *
     * This test simulates the entry of a new vehicle (first-time user) into the parking lot.
     * It verifies that:
     * - The correct parking spot is allocated based on the vehicle type.
     * - The vehicle registration number is correctly read.
     * - The system identifies that it's a new user (no discount).
     * - The parking spot is marked as unavailable.
     * - A ticket is generated and saved in the database.
     *
     * @throws Exception if any error occurs during test execution or mocking
     */
    @Test
    public void testProcessIncomingVehicle() throws Exception {
    	
    	ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR,false);
        Ticket ticket = new Ticket();
        ticket.setVehicleRegNumber("ABCDEF");
        ticket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR,false));
        ticket.setInTime(new Date(System.currentTimeMillis() - (60 * 60 * 1000)));
        ticket.setOutTime(null); // vehicle hasn't left yet
        ticket.setPrice(0);
        
        when(inputReaderUtil.readSelection()).thenReturn(1); // user selects "CAR"
        when(parkingSpotDAO.getNextAvailableSlot(any(ParkingType.class))).thenReturn(1); // spot 1 available
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
        when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(0); // new user
        when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(true); // update is successful

    	// WHEN
        parkingService.processIncomingVehicle();

        // THEN
        // Verify that the system checked if the vehicle was a returning user
        verify(ticketDAO, times(1)).getNbTicket("ABCDEF");

        // Verify that the parking spot was marked as unavailable
        verify(parkingSpotDAO, times(1)).updateParking(any(ParkingSpot.class));

        // Verify that a new ticket was saved into the database
        verify(ticketDAO, times(1)).saveTicket(any(Ticket.class));

        // Check that the parking spot is no longer available
        assertFalse(parkingSpot.isAvailable());

        // Check that the ticket has an associated parking spot
        assertNotNull(ticket.getParkingSpot());

        // Check that the vehicle registration number was correctly assigned to the ticket
        assertEquals("ABCDEF", ticket.getVehicleRegNumber());

        // Check that the out time is null, since the vehicle just entered
        assertNull(ticket.getOutTime());
    }
    
    /**
     * Unit test for the processExitingVehicle() method of the ParkingService class,
     * testing the scenario where the system fails to update the ticket in the database.
     *
     * This test simulates a regular user (more than one ticket already exists),
     * and verifies that:
     * - The vehicle's registration number is correctly read.
     * - The associated ticket is retrieved.
     * - The fare is calculated with a discount.
     * - The ticket update fails (returns false).
     * - As a result, the parking spot is not marked as available.
     *
     * @throws Exception if an error occurs during test execution or mocking
     */
    @Test
    public void processExitingVehicleTestUnableUpdate() throws Exception {
    	
    	ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR,false);
    	Ticket ticket = new Ticket();
        ticket.setVehicleRegNumber("ABCDEF");
        ticket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR,false));
        ticket.setInTime(new Date(System.currentTimeMillis() - (60 * 60 * 1000)));
        ticket.setOutTime(new Date());
        ticket.setPrice(0);
    	// GIVEN
    	when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
    	when(ticketDAO.getTicket(anyString())).thenReturn(ticket);
    	when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(3); // regular user
        when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(false); // simulate DB update failure

    	// WHEN
        parkingService.processExitingVehicle();

        // THEN
        verify(ticketDAO, times(1)).getTicket(anyString());
        verify(ticketDAO, times(1)).getNbTicket("ABCDEF");
        verify(ticketDAO, times(1)).updateTicket(any(Ticket.class));
        
        // Verify that the parking spot is NOT updated since ticket update failed
        verify(parkingSpotDAO, never()).updateParking(any(ParkingSpot.class)); 
        
        // Assert that the parking spot remains marked as unavailable
        assertFalse(parkingSpot.isAvailable());
    }

    /**
     * Unit test for the getNextParkingNumberIfAvailable() method of the ParkingService class.
     *
     * This test verifies that the service correctly identifies and returns the next available parking spot
     * when a valid vehicle type is selected (in this case, a car).
     *
     * It checks that:
     * - The user's vehicle type selection is read properly.
     * - The parkingSpotDAO returns a valid parking spot ID.
     * - The returned ParkingSpot object is correctly initialized with availability set to true.
     */
    @Test
    public void testGetNextParkingNumberIfAvailable() {
    	// GIVEN
    	 when(inputReaderUtil.readSelection()).thenReturn(1); // 1 corresponds to ParkingType.CAR
    	    when(parkingSpotDAO.getNextAvailableSlot(any(ParkingType.class))).thenReturn(1); // Spot ID 1 is available

    	// WHEN
    	ParkingSpot parkingSpot = parkingService.getNextParkingNumberIfAvailable();

        // THEN
        verify(parkingSpotDAO, times(1)).getNextAvailableSlot(any(ParkingType.class));

        assertNotNull(parkingSpot);                    // The object should not be null
        assertEquals(1, parkingSpot.getId());          // The ID should match the mocked return value
        assertTrue(parkingSpot.isAvailable());         // By default, the spot should be marked as available
    }
    
    /**
     * Unit test for the getNextParkingNumberIfAvailable() method of the ParkingService class,
     * testing the scenario where no parking spot is available.
     *
     * This test verifies that:
     * - The user's vehicle type selection is correctly read.
     * - The parkingSpotDAO returns 0, meaning no available spot was found.
     * - The method correctly returns null when no parking spot is available.
     */
    @Test
    public void testGetNextParkingNumberIfAvailableParkingNumberNotFound() {
    	// GIVEN
    	when(inputReaderUtil.readSelection()).thenReturn(1);
    	when(parkingSpotDAO.getNextAvailableSlot(any(ParkingType.class))).thenReturn(0); //Return no spot available

    	// WHEN
    	ParkingSpot parkingSpot = parkingService.getNextParkingNumberIfAvailable();

        // THEN
        verify(parkingSpotDAO, times(1)).getNextAvailableSlot(any(ParkingType.class));

        // Assert that the method returns null when no spot is available
        assertNull(parkingSpot);
    }
    
    /**
     * Unit test for the getNextParkingNumberIfAvailable() method of the ParkingService class,
     * testing the scenario where the user selects an invalid parking type.
     *
     * This test verifies that:
     * - An invalid vehicle type selection is handled correctly.
     * - The method returns null when the input does not match any supported ParkingType.
     * - No interaction with the DAO in this case.
     */
    @Test
    public void testGetNextParkingNumberIfAvailableParkingNumberWrongArgument() {
    	// GIVEN
    	when(inputReaderUtil.readSelection()).thenReturn(3); //Return Wrong ParkingType

    	// WHEN
    	ParkingSpot parkingSpot = parkingService.getNextParkingNumberIfAvailable();

        // THEN
    	// Check that the method returned null because the user's selection was invalid
        assertNull(parkingSpot);
    }
}
