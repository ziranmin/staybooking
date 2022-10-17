package com.laioffer.staybooking.service;

import com.laioffer.staybooking.model.*;
import org.springframework.stereotype.Service;

import com.laioffer.staybooking.repository.ReservationRepository;
import com.laioffer.staybooking.repository.StayReservationDateRepository;
import org.springframework.beans.factory.annotation.Autowired;

import com.laioffer.staybooking.exception.ReservationCollisionException;
import com.laioffer.staybooking.exception.ReservationNotFoundException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
public class ReservationService {
    private ReservationRepository reservationRepository;
    private StayReservationDateRepository stayReservationDateRepository;

    @Autowired
    public ReservationService(ReservationRepository reservationRepository, StayReservationDateRepository stayReservationDateRepository) {
        this.reservationRepository = reservationRepository;
        this.stayReservationDateRepository = stayReservationDateRepository;
    }

    public List<Reservation> listByGuest(String username) {
        return reservationRepository.findByGuest(new User.Builder().setUsername(username).build());
    }

    public List<Reservation> listByStay(Long stayId) {
        return reservationRepository.findByStay(new Stay.Builder().setId(stayId).build());
    }


    @Transactional(isolation = Isolation.SERIALIZABLE) // stayReservationDateRepository和reservationRepository要加一起加，要删一起删
    public void add(Reservation reservation) throws ReservationCollisionException {
        // Check collision
        Set<Long> stayIds = stayReservationDateRepository.findByIdInAndDateBetween(
                Arrays.asList(reservation.getStay().getId()),
                reservation.getCheckinDate(),
                reservation.getCheckoutDate().minusDays(1));
        if (!stayIds.isEmpty()) {
            throw new ReservationCollisionException("Duplicate reservation");
        }

        // save reserved date to MySQL
        List<StayReservedDate> reservedDates = new ArrayList<>();
        for (LocalDate date = reservation.getCheckinDate();
             date.isBefore(reservation.getCheckoutDate());
             date = date.plusDays(1)) {

                reservedDates.add(
                        new StayReservedDate(
                             new StayReservedDateKey(reservation.getStay().getId(), date),
                             reservation.getStay()
                        )
                );

        }
        // save date 可能会save多条, 所以用save all
        stayReservationDateRepository.saveAll(reservedDates);

        // save reservation to MySQL
        reservationRepository.save(reservation);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE) // stayReservationDateRepository和reservationRepository要加一起加，要删一起删
    public void delete(Long reservationId, String username) {

        // Check if reservation exists
        Reservation reservation = reservationRepository.findByIdAndGuest(
                reservationId,
                new User.Builder().setUsername(username).build()
        );
        if (reservation == null) {
            throw new ReservationNotFoundException("Reservation is not available");
        }

        // delete reserved date from MySQL, 下面是一个一个date删，但是其实可以用deleteAll
        for (LocalDate date = reservation.getCheckinDate();
             date.isBefore(reservation.getCheckoutDate());
             date = date.plusDays(1)) {

            stayReservationDateRepository.deleteById(
                    new StayReservedDateKey(reservation.getStay().getId(), date)
            );

        }

        // delete reservation in MySQL
        reservationRepository.deleteById(reservationId);
    }
}




