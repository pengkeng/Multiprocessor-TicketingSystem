package ticketingsystem;

import java.util.ArrayList;
import java.util.Random;

public class TicketingDS implements TicketingSystem {

    private int threadnum;
    private int routenum;
    private int coachnum;
    private int seatnum;
    private int stationnum;
    private ArrayList<ArrayList<Seat>> TicketPool = new ArrayList<>();
    private Random rand;

    /**
     * 每条路线的座位数 coachnum*seatnum
     */
    private int count;

    /**
     * 系统初始化
     *
     * @param routenum   //
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
        rand = new Random();

        //初始化座位
        for (int i = 0; i < this.routenum; i++) {
            ArrayList<Seat> routePool = new ArrayList<>();
            for (int j = 0; j < this.coachnum; j++) {
                for (int k = 0; k < seatnum; k++) {
                    Seat seat = new Seat(i + 1, j + 1, k + 1, this.stationnum);
                    routePool.add(seat);
                }
            }
            TicketPool.add(routePool);
        }
    }

    /**
     * 每次买票从对应的routePool中取票
     *
     * @param passenger
     * @param route
     * @param departure
     * @param arrival
     * @return
     */
    @Override
    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        //拿到对应路线
        ArrayList<Seat> routePool = TicketPool.get(route - 1);
        //每次随机一个座位序号，进行买票
        int length = routePool.size();
        int randomStart = Math.abs(passenger.hashCode() % length);
        //对所有位置进行遍历
        for (int i = 0; i < length; i++) {
            Seat seat = routePool.get(randomStart);
            randomStart = (randomStart + 1) % length;
            Ticket ticket = seat.buyTicket(passenger, departure, arrival);
            if (ticket != null) {
                return ticket;
            }
        }
        return null;
    }

    /**
     * 只读操作，无需互斥
     *
     * @param route
     * @param departure
     * @param arrival
     * @return
     */
    @Override
    public int inquiry(int route, int departure, int arrival) {
        //拿到对应路线
        ArrayList<Seat> routePool = TicketPool.get(route - 1);
        //遍历所有的座位
        int count = 0;
        for (Seat seat : routePool) {
            int result = seat.inquiry(departure, arrival);
            if (result == 1) {
                count++;
            }
        }
        return count;
    }

    /**
     * 退票，可以多人同时操作一个对象，无需互斥
     *
     * @param ticket
     * @return
     */
    @Override
    public boolean refundTicket(Ticket ticket) {
        int route = ticket.route;
        int coachId = ticket.coach;
        int seatId = ticket.seat;
        int index = (coachId - 1) * seatnum + seatId - 1;
        Seat seat = TicketPool.get(route - 1).get(index);
        return seat.refundTicket(ticket);
    }
}

/**
 * 座位对象，需要互斥
 */
class Seat {
    private int routenum;
    private int coachnum;
    private int[] currentSeats;
    private int seatnum;
    private int stationnum;
    private ArrayList<Ticket> tickets = new ArrayList<>();


    Seat(int routenum, int coachnum, int seatnum, int stationnum) {
        this.routenum = routenum;
        this.coachnum = coachnum;
        this.seatnum = seatnum;
        this.stationnum = stationnum;
        currentSeats = new int[this.stationnum];

        // 初始化座位，每个站次均空闲
        for (int i = 0; i < stationnum; i++) {
            currentSeats[i] = 0;
        }
    }

    /**
     * 当前座位出售区间票 （同时只能有一个线程对该座位进行买票）
     *
     * @param passenger
     * @param departure
     * @param arrival
     * @return
     */
    public synchronized Ticket buyTicket(String passenger, int departure, int arrival) {

        //判断该座位是否还有票（对应区间）
        for (int i = departure - 1; i < arrival - 1; i++) {
            if (currentSeats[i] == 1) {
                return null;
            }
        }
        //新建一个票object
        Ticket ticket = new Ticket();
        ticket.passenger = passenger;
        ticket.route = routenum;
        ticket.coach = coachnum;
        ticket.seat = seatnum;
        ticket.departure = departure;
        ticket.arrival = arrival;
        ticket.tid = ticket.hashCode();
        tickets.add(ticket);

        // 当前区间已无票
        for (int i = departure - 1; i < arrival - 1; i++) {
            currentSeats[i] = 1;
        }
        return ticket;
    }

    /**
     * 该座位区间退票（由于票已经指定，锁粒度足够细，可以加锁）
     *
     * @return
     */
    public synchronized boolean refundTicket(Ticket ticket) {
        boolean isRight = false;
        for (Ticket item : tickets) {
            if (item != null && item.tid == ticket.tid) {
                isRight = true;
                tickets.remove(item);
                break;
            }
        }
        if (isRight) {

            int departure = ticket.departure;
            int arrival = ticket.arrival;
            // 将该区间设为空闲，可售票
            for (int i = departure - 1; i < arrival - 1; i++) {
                currentSeats[i] = 0;
            }
            return true;
        } else {
            return false;
        }
    }


    /**
     * 查询票，可以多个对象同时操作，允许误差存在
     *
     * @param departure
     * @param arrival
     * @return
     */
    public int inquiry(int departure, int arrival) {
        // 如果该区间存在已出售的站台，返回0，表示无票
        for (int i = departure - 1; i < arrival - 1; i++) {
            if (currentSeats[i] == 1) {
                return 0;
            }
        }
        return 1;
    }
}
