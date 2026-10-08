package edu.sjsu.scheduler;

import edu.sjsu.scheduler.exception.ConflictException;
import edu.sjsu.scheduler.exception.ForbiddenException;
import edu.sjsu.scheduler.exception.InvalidRequestException;
import edu.sjsu.scheduler.exception.SlotAlreadyBookedException;
import edu.sjsu.scheduler.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real PostgreSQL database (schema.sql + seed.sql are reloaded on startup).
 * Slot ids come from seed.sql. Each test uses a different slot so they don't affect each other.
 *   slot 1 = already booked by student_alex
 *   slot 2, 3, 4 = open
 *   slot 5 = had a CANCELLED booking (so it is open again)
 *   slot 9 = in the past
 */
@SpringBootTest
class BookingServiceTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JdbcTemplate jdbc;

    private int bookedCount(long slotId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'",
                Integer.class, slotId);
        return n == null ? 0 : n;
    }

    @Test
    void twoStudentsBookSameSlotAtSameTime_onlyOneSucceeds() throws Exception {
        long slotId = 2;
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<Long> maya = () -> {
            startGate.await();
            return bookingService.bookSlot(slotId, "student_maya", "thread A");
        };
        Callable<Long> leo = () -> {
            startGate.await();
            return bookingService.bookSlot(slotId, "student_leo", "thread B");
        };

        List<Future<Long>> futures = List.of(pool.submit(maya), pool.submit(leo));
        startGate.countDown();   // release both threads at the same moment

        int success = 0;
        int conflict = 0;
        for (Future<Long> f : futures) {
            try {
                f.get(10, TimeUnit.SECONDS);
                success++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(SlotAlreadyBookedException.class);
                conflict++;
            }
        }
        pool.shutdown();

        assertThat(success).isEqualTo(1);
        assertThat(conflict).isEqualTo(1);
        assertThat(bookedCount(slotId)).isEqualTo(1);
    }

    @Test
    void bookingAnAlreadyBookedSlot_throws409() {
        assertThatThrownBy(() -> bookingService.bookSlot(1, "student_maya", null))
                .isInstanceOf(SlotAlreadyBookedException.class);
        assertThat(bookedCount(1)).isEqualTo(1);
    }

    @Test
    void databaseIndexBlocksDoubleBookingEvenWithoutServiceChecks() {
        // Insert straight into the table, skipping the service layer.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO appointments (customer_id, slot_id, service_id, status) VALUES (4, 1, 1, 'BOOKED')"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void onlyOwnerCanCancel() {
        long apptId = bookingService.bookSlot(3, "student_maya", null);

        assertThatThrownBy(() -> bookingService.cancel(apptId, "student_leo"))
                .isInstanceOf(ForbiddenException.class);
        assertThat(bookedCount(3)).isEqualTo(1);

        bookingService.cancel(apptId, "student_maya");
        assertThat(bookedCount(3)).isZero();

        // cancelling twice is a conflict
        assertThatThrownBy(() -> bookingService.cancel(apptId, "student_maya"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelledSlotCanBeBookedAgain() {
        long first = bookingService.bookSlot(4, "student_maya", null);
        bookingService.cancel(first, "student_maya");

        long second = bookingService.bookSlot(4, "student_leo", "rebooking");
        assertThat(second).isNotEqualTo(first);
        assertThat(bookedCount(4)).isEqualTo(1);
    }

    @Test
    void seededCancelledSlotIsBookable() {
        bookingService.bookSlot(5, "student_leo", null);
        assertThat(bookedCount(5)).isEqualTo(1);
    }

    @Test
    void pastSlotCannotBeBooked() {
        assertThatThrownBy(() -> bookingService.bookSlot(9, "student_leo", null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void advisorCannotBookAsStudent() {
        assertThatThrownBy(() -> bookingService.bookSlot(6, "advisor_kim", null))
                .isInstanceOf(ForbiddenException.class);
    }
}
