package ticketingsystem;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

public class TicketingDS implements TicketingSystem {

    private int threadnum;
    private int routenum;
    private int coachnum;
    private int seatnum;
    private int stationnum;//
    public static ArrayList<ArrayList<Seat>> TicketPool = new ArrayList<>();
    public static ArrayList<AtomicIntegerArray> TicketPoolCount = new ArrayList<>();//该数据结构使用 ReentrantLock, 写加锁，读不加锁
    public Random random;

    /**
     * coachnum*seatnum
     */
    public static int count;

    /**
     * @param routenum
     * @param coachnum
     * @param seatnum
     * @param stationnum
     * @param threadnum
     */
    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) {
        this.routenum = routenum;
        this.coachnum = coachnum;
        this.seatnum = seatnum;
        this.stationnum = stationnum;
        this.threadnum = threadnum;
        count = coachnum * seatnum;
        random = new Random();

        for (int i = 0; i < this.routenum; i++) {
            ArrayList<Seat> routePool = new ArrayList<>();
            for (int j = 0; j < this.coachnum; j++) {
                for (int k = 0; k < seatnum; k++) {
                    Seat seat = new Seat(i + 1, j + 1, k + 1, this.stationnum);
                    routePool.add(seat);
                }
            }
            TicketPool.add(routePool);
            AtomicIntegerArray routePoolCount = new AtomicIntegerArray(100);
            for (int j = 0; j < 100; j++) {
                routePoolCount.set(j, count);
            }
            TicketPoolCount.add(routePoolCount);
        }
    }

    /**
     * @param passenger
     * @param route
     * @param departure
     * @param arrival
     * @return
     */
    @Override
    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        if (inquiry(route, departure, arrival) <= 0) {
            return null;
        }
        int index = count / threadnum * ThreadId.get();
        for (int i = 0; i < count; i++) {
            index = index % count;
            Seat seat = TicketPool.get(route - 1).get(index);
            index++;
            if (seat.inquiry(departure, arrival)) {
                Ticket ticket = seat.buyTicket(passenger, departure, arrival);
                if (ticket != null) {
                    return ticket;
                }
            }
        }
        return null;
    }

    /**
     * @param route
     * @param departure
     * @param arrival
     * @return
     */
    @Override
    public int inquiry(int route, int departure, int arrival) {
        if (arrival == 10) {
            arrival = 0;
        }
        return TicketPoolCount.get(route - 1).get(departure * 10 + arrival);
    }

    /**
     * @param ticket
     * @return
     */
    @Override
    public boolean refundTicket(Ticket ticket) {
        long code = (long) ticket.arrival + ticket.departure * 100L + (ticket.seat - 1) * 10000L + ticket.coach * 10000000L + ticket.route * 1000000000L;
        long id = ticket.tid % 100000000000L;

        if (id == code) {
            int route = ticket.route;
            int coachId = ticket.coach;
            int seatId = ticket.seat;
            int index = (coachId - 1) * seatnum + seatId - 1;
            Seat seat = TicketPool.get(route - 1).get(index);
            return seat.refundTicket(ticket);
        }
        return false;
    }
}

class Seat {
    private int routenum;
    private int coachnum;
    private int seatnum;
    private int stationnum;
    public int stateBinary;
    private int stateFull;
    private AtomicLong tidAdd = new AtomicLong();

    Seat(int route, int coach, int seat, int station) {
        this.routenum = route;
        this.coachnum = coach;
        this.seatnum = seat;
        this.stationnum = station;
        stateBinary = 0;
        stateFull = (1 << this.stationnum) - 1;
        tidAdd.set(0);
    }

    /**
     * @param passenger
     * @param departure
     * @param arrival
     * @return
     */
    public synchronized Ticket buyTicket(String passenger, int departure, int arrival) {
        int ticketState = ((1 << (arrival - 1)) - 1) ^ ((1 << (departure - 1)) - 1);
        if ((stateBinary & ticketState) == 0) {
            long code = (long) arrival + departure * 100L + (seatnum - 1) * 10000L + coachnum * 10000000L + routenum * 1000000000L;
            long tid = tidAdd.getAndIncrement() * 100000000000L + code;
            Ticket ticket = new Ticket(passenger, routenum, coachnum, seatnum, departure, arrival, tid);
            stateBinary = stateBinary | ticketState;
            int start = departure;
            for (int i = departure - 1; i >= 1; i--) {
                if (((stateBinary >> (i - 1)) & 1) == 1) {
                    break;
                }
                start = i;
            }
            int end = arrival - 1;
            for (int i = arrival; i <= stationnum; i++) {
                if (((stateBinary >> (i - 1)) & 1) == 1) {
                    break;
                }
                end = i;
            }
            if (end == 10) {
                end = 9;
            }
            for (int i = start; i < arrival; i++) {
                for (int j = departure + 1; j <= end + 1; j++) {
                    if (j > i) {
                        int index;
                        if (j == 10) {
                            index = i * 10;
                        } else {
                            index = i * 10 + j;
                        }
                        if (TicketingDS.TicketPoolCount.get(routenum - 1).decrementAndGet(index) < 0) {
                            TicketingDS.TicketPoolCount.get(routenum - 1).set(index, 0);
                        }
                    }
                }
            }

            return ticket;
        } else {
            return null;
        }
    }

    /**
     * @return
     */
    public synchronized boolean refundTicket(Ticket ticket) {
        int departure = ticket.departure;
        int arrival = ticket.arrival;
        int ticketState = (stateFull >> (departure - 1) << (departure - 1)) ^ ((1 << (arrival - 1)) - 1);
        stateBinary = stateBinary & ticketState;
        int start = departure;
        for (int i = departure - 1; i >= 1; i--) {
            if (((stateBinary >> (i - 1)) & 1) == 1) {
                break;
            }
            start = i;
        }
        int end = arrival - 1;
        for (int i = arrival; i <= stationnum; i++) {
            if (((stateBinary >> (i - 1)) & 1) == 1) {
                break;
            }
            end = i;
        }
        if (end == 10) {
            end = 9;
        }
        for (int i = start; i < arrival; i++) {
            for (int j = departure + 1; j <= end + 1; j++) {
                if (j > i) {
                    int index;
                    if (j == 10) {
                        index = i * 10;
                    } else {
                        index = i * 10 + j;
                    }
                    if (TicketingDS.TicketPoolCount.get(ticket.route - 1).incrementAndGet(index) > TicketingDS.count) {
                        TicketingDS.TicketPoolCount.get(ticket.route - 1).set(index, TicketingDS.count);
                    }
                }
            }
        }
        return true;
    }


    /**
     * @param departure
     * @param arrival
     * @return
     */
    public boolean inquiry(int departure, int arrival) {
        int ticketState = ((1 << (arrival - 1)) - 1) ^ ((1 << (departure - 1)) - 1);
        return (stateBinary & ticketState) == 0;
    }
}