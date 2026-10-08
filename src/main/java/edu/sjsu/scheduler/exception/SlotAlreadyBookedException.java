package edu.sjsu.scheduler.exception;

/** Thrown when a student tries to book a slot that already has a BOOKED appointment (HTTP 409). */
public class SlotAlreadyBookedException extends ConflictException {

    public SlotAlreadyBookedException(long slotId) {
        super("Availability slot " + slotId + " is already booked.");
    }
}
