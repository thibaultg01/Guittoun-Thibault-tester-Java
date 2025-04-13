package com.parkit.parkingsystem.service;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.model.Ticket;

public class FareCalculatorService {

	/**
	 * Calculates the fare for a given ticket without applying any discount.
	 * <p>
	 * This method delegates to the overloaded version
	 * of {@code calculateFare(Ticket, boolean)} with {@code discount} set to {@code false}.
	 * </p>
	 *
	 * @param ticket the ticket containing entry and exit times, and parking spot details
	 * @throws IllegalArgumentException if the out time is null or before the in time
	 */
	public void calculateFare(Ticket ticket) {
		calculateFare(ticket, false);
	}

	/**
	 * Calculates the fare for a given ticket, with optional discount.
	 * <p>
	 * The method computes the parking duration in hours and determines the price
	 * based on the vehicle type and the duration. If the duration is less than or
	 * equal to 30 minutes, parking is free. If a discount is requested, a 5%
	 * reduction is applied to the calculated fare.
	 * </p>
	 *
	 * @param ticket the ticket containing entry and exit times, and parking spot details
	 * @param discount {@code true} to apply a 5% discount to the fare, {@code false} otherwise
	 * @throws IllegalArgumentException if the out time is null or occurs before the in time,
	 *                                  or if the parking type is unknown
	 */
	public void calculateFare(Ticket ticket, boolean discount) {
		if ((ticket.getOutTime() == null) || (ticket.getOutTime().before(ticket.getInTime()))) {
			throw new IllegalArgumentException("Out time provided is incorrect:" + ticket.getOutTime().toString());
		}

		// Retrieve timestamps in milliseconds
		long inTime = ticket.getInTime().getTime();
		long outTime = ticket.getOutTime().getTime();

		// Calculate the duration in hours
		double duration = (double) (outTime - inTime) / (1000 * 60 * 60);

		// Free if parking for 30 minutes or less
		if (duration <= 0.5) {
			ticket.setPrice(0);
		} else {
			switch (ticket.getParkingSpot().getParkingType()) {
			case CAR: {
				ticket.setPrice(duration * Fare.CAR_RATE_PER_HOUR);
				break;
			}
			case BIKE: {
				ticket.setPrice(duration * Fare.BIKE_RATE_PER_HOUR);
				break;
			}
			default:
				throw new IllegalArgumentException("Unkown Parking Type");
			}

			// Application of the reduction if discount
			if (discount) {
				double Fare = 0;
				Fare += ticket.getPrice();
				ticket.setPrice(Fare * 0.95);// 5% reduction
			}
		}
	}
}