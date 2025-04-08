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
    private static void setUp() throws Exception{
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
    private static void tearDown(){

    }

    @Test
    public void testParkingACar(){
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processIncomingVehicle();
        //TODO: check that a ticket is actualy saved in DB and Parking table is updated with availability
        Ticket ticket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticket);
        assertEquals("ABCDEF", ticket.getVehicleRegNumber());
        ParkingSpot parkingSpot = ticket.getParkingSpot();
        assertNotNull(parkingSpot);
        assertFalse(parkingSpot.isAvailable());
    }

    @Test
    public void testParkingLotExit() throws InterruptedException{
        testParkingACar();
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        //TODO: check that the fare generated and out time are populated correctly in the database
        Thread.sleep(1000);
        parkingService.processExitingVehicle();
        Ticket ticket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticket.getOutTime());
        assertEquals(0.0, ticket.getPrice());
    }
    
    @Test
    public void testParkingLotExitRecurringUser() throws InterruptedException{
        testParkingLotExit();
    	ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        /*parkingService.processIncomingVehicle();
        Thread.sleep(1000);
        parkingService.processExitingVehicle();*/

        parkingService.processIncomingVehicle();
        Timestamp timestampinTimeTicket1 = new Timestamp(System.currentTimeMillis() - (24* 60 * 60 * 1000));
        Timestamp timestampoutTimeTicket1 = new Timestamp(System.currentTimeMillis() - (2* 60 * 60 * 1000));
        Timestamp timestampinTimeTicket2 = new Timestamp(System.currentTimeMillis() - (60 * 60 * 1000));

        Connection con = null;
        try {
            con = dataBaseTestConfig.getConnection();
            PreparedStatement ps1 = con.prepareStatement("update ticket set IN_TIME=?, OUT_TIME=? where VEHICLE_REG_NUMBER='ABCDEF' AND ID=1");

            ps1.setTimestamp(1, new Timestamp(timestampinTimeTicket1.getTime()));
            ps1.setTimestamp(2, new Timestamp(timestampoutTimeTicket1.getTime()));
           ps1.execute();
           PreparedStatement ps2 = con.prepareStatement("update ticket set IN_TIME=? where VEHICLE_REG_NUMBER='ABCDEF' AND ID=2");
            ps2.setTimestamp(1, new Timestamp(timestampinTimeTicket2.getTime()));
            ps2.execute();
            parkingService.processExitingVehicle();
        }catch (Exception ex){
            logger.error("Error saving ticket info",ex);
        }finally {
            dataBaseTestConfig.closeConnection(con);
        }
        //Ticket ticket2 = ticketDAO.getTicket("ABCDEF");
       // fareCalculatorService.calculateFare(ticket2, true);
        double value1 = Fare.CAR_RATE_PER_HOUR * 0.95;
        BigDecimal bd1 = new BigDecimal(value1);
        bd1= bd1.setScale(1, RoundingMode.HALF_UP);
        double arround1 = bd1.doubleValue();
        double value2 = ticketDAO.getTicket("ABCDEF").getPrice();
        BigDecimal bd2 = new BigDecimal(value2);
        bd2 = bd2.setScale(1, RoundingMode.HALF_UP);
        double arround2 = bd2.doubleValue();
        assertEquals(arround1, arround2);
    	}
    }
