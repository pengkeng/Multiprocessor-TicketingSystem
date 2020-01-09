package ticketingsystem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class TicketingDS implements TicketingSystem {

    private int threadnum;
    private int routenum;
    private int coachnum;
    private int seatnum;
    private int stationnum;//最大为10
    public static ArrayList<ArrayList<Seat>> TicketPool = new ArrayList<>();
    public static ArrayList<AtomicIntegerArray> TicketPoolCount = new ArrayList<>();//该数据结构使用 ReentrantLock, 写加锁，读不加锁
    public Random random;

    /**
     * 每条路线的座位数 coachnum*seatnum
     */
    public static int count;

    /**
     * 系统初始化
     *
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
            AtomicIntegerArray routePoolCount = new AtomicIntegerArray(100);
            for (int j = 0; j < 100; j++) {
                routePoolCount.set(j, count);
            }
            TicketPoolCount.add(routePoolCount);
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

        if (inquiry(route, departure, arrival) <= 0) {
            return null;
        }
        //每次随机一个座位序号，进行买票
        //对所有位置进行遍历
        int i;
        for (i = 0; i < count; i++) {
            Seat seat = TicketPool.get(route - 1).get(i);
            Ticket ticket = seat.buyTicket(passenger, departure, arrival);
            if (ticket != null) {
                TicketingDS.TicketPool.get(route - 1).set(i, seat);
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
        if (arrival == 10) {
            arrival = 0;
        }
        return TicketPoolCount.get(route - 1).get(departure * 10 + arrival);
    }

    /**
     * 退票，可以多人同时操作，无需互斥
     *
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

/**
 * 座位对象，需要互斥
 */
class Seat {
    private int routenum;
    private int coachnum;
    private int seatnum;
    private int stationnum;
    public AtomicInteger stateBinary;
    private int stateFull;
    private AtomicLongArray tidSet = new AtomicLongArray(100);
    private AtomicLong tidAdd = new AtomicLong();

    Seat(int route, int coach, int seat, int station) {
        this.routenum = route;
        this.coachnum = coach;
        this.seatnum = seat;
        this.stationnum = station;
        stateBinary = new AtomicInteger(0);
        stateFull = (1 << this.stationnum) - 1;
        tidAdd.set(0);
    }

    /**
     * 当前座位出售区间票 （同时只能有一个线程对该座位进行买票）这个锁是主要的影响性能的地方，考虑换一种CAS来实现
     *
     * @param passenger
     * @param departure
     * @param arrival
     * @return
     */
    public Ticket buyTicket(String passenger, int departure, int arrival) {
        int ticketState = ((1 << (arrival - 1)) - 1) ^ ((1 << (departure - 1)) - 1);
        //判断是否还有票
        if ((stateBinary.get() & ticketState) == 0) {
            int last = stateBinary.get();
            if (stateBinary.compareAndSet(last, stateBinary.get() | ticketState)) {
                long code = (long) arrival + departure * 100L + (seatnum - 1) * 10000L + coachnum * 10000000L + routenum * 1000000000L;
                long tid = tidAdd.getAndIncrement() * 100000000000L + code;
                if (tidSet.compareAndSet(departure * 10 + arrival % 10, 0, tid)) {
                    Ticket ticket = new Ticket(passenger, routenum, coachnum, seatnum, departure, arrival, tid);
                    AtomicIntegerArray routePoolCount = TicketingDS.TicketPoolCount.get(routenum - 1);
                    //i表示发站，j表示到站,对于 90 表示从9站到10站
                    int start = departure;
                    for (int i = departure - 1; i >= 1; i--) {
                        if (((stateBinary.get() >> (i - 1)) & 1) == 1) {
                            break;
                        }
                        start = i;
                    }
                    int end = arrival - 1;
                    for (int i = arrival; i <= stationnum; i++) {
                        if (((stateBinary.get() >> (i - 1)) & 1) == 1) {
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
                                if (routePoolCount.decrementAndGet(index) < 0) {
                                    routePoolCount.set(index, 0);
                                }
                            }
                        }
                    }
                    return ticket;
                }
            }
        } else {
            return null;
        }
        return null;
    }


    /**
     * 该座位区间退票（已验证票的合法性，需要加锁）
     *
     * @return
     */
    public boolean refundTicket(Ticket ticket) {
        if (tidSet.get(ticket.departure * 10 + ticket.arrival % 10) != ticket.tid) {
//            System.out.println("333");
            return false;
        }
        int departure = ticket.departure;
        int arrival = ticket.arrival;
        String str = Integer.toBinaryString(stateBinary.get());
//        if (!inquiry(departure, arrival)) {
            //更新票池状态
            int ticketState = (stateFull >> (departure - 1) << (departure - 1)) ^ ((1 << (arrival - 1)) - 1);
            long lastTid = tidSet.get(ticket.departure * 10 + ticket.arrival % 10);
            tidSet.compareAndSet(ticket.departure * 10 + ticket.arrival % 10, lastTid, 0);
            while (true) {
                int last = stateBinary.get();
                if (stateBinary.compareAndSet(last, stateBinary.get() & ticketState)) {
                    AtomicIntegerArray routePoolCount = TicketingDS.TicketPoolCount.get(ticket.route - 1);
                    int start = departure;
                    for (int i = departure - 1; i >= 1; i--) {
                        if (((stateBinary.get() >> (i - 1)) & 1) == 1) {
                            break;
                        }
                        start = i;
                    }
                    int end = arrival - 1;
                    for (int i = arrival; i <= stationnum; i++) {
                        if (((stateBinary.get() >> (i - 1)) & 1) == 1) {
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
                                if (routePoolCount.incrementAndGet(index) > TicketingDS.count) {
                                    routePoolCount.set(index, TicketingDS.count);
                                }
                            }
                        }
                    }
                    return true;
                }
            }
//        } else {
//            System.out.println("111");
//            return false;
//        }
    }


    /**
     * 查询该座位是否还有票
     * 返回0，表示无票;否则返回1
     *
     * @param departure
     * @param arrival
     * @return
     */
    public boolean inquiry(int departure, int arrival) {
        int ticketState = ((1 << (arrival - 1)) - 1) ^ ((1 << (departure - 1)) - 1);
        if ((stateBinary.get() & ticketState) == 0) {
            return true;
        } else {
            return false;
        }
    }
}
