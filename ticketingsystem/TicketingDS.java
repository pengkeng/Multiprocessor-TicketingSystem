package ticketingsystem;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class TicketingDS implements TicketingSystem {

    private int threadnum;
    private int routenum;
    private int coachnum;
    private int seatnum;
    private int stationnum;//最大为10
    public static CopyOnWriteArrayList<CopyOnWriteArrayList<Seat>> TicketPool = new CopyOnWriteArrayList<>();
    public static CopyOnWriteArrayList<AtomicIntegerArray> TicketPoolCount = new CopyOnWriteArrayList<>();//该数据结构使用 ReentrantLock, 写加锁，读不加锁

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

        //初始化座位
        for (int i = 0; i < this.routenum; i++) {
            CopyOnWriteArrayList<Seat> routePool = new CopyOnWriteArrayList<>();
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
     * 退票，可以多人同时操作一个对象，无需互斥
     *
     * @param ticket
     * @return
     */
    @Override
    public boolean refundTicket(Ticket ticket) {
        if (ticket.isValid()) {
            int route = ticket.route;
            int coachId = ticket.coach;
            int seatId = ticket.seat;
            int index = (coachId - 1) * seatnum + seatId - 1;
            Seat seat = TicketPool.get(route - 1).get(index);
            return seat.refundTicket(ticket);
        } else {
            return false;
        }
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
    public int stateBinary;
    private int stateFull;

    Seat(int route, int coach, int seat, int station) {
        this.routenum = route;
        this.coachnum = coach;
        this.seatnum = seat;
        this.stationnum = station;
        stateBinary = 0;
        stateFull = (1 << this.stationnum) - 1;
    }

    /**
     * 当前座位出售区间票 （同时只能有一个线程对该座位进行买票）这个锁是主要的影响性能的地方，考虑换一种CAS来实现
     *
     * @param passenger
     * @param departure
     * @param arrival
     * @return
     */
    public synchronized Ticket buyTicket(String passenger, int departure, int arrival) {
        int ticketState = ((1 << (arrival - 1)) - 1) ^ ((1 << (departure - 1)) - 1);
        //判断是否还有票
        if ((stateBinary & ticketState) == 0) {
            String tid = routenum * 10000 + coachnum * 1000 + seatnum * 100 + departure * 10 + arrival + passenger;
            Ticket ticket = new Ticket(passenger, routenum, coachnum, seatnum, departure, arrival, tid.hashCode());
            int tempState = stateBinary | ticketState;
            AtomicIntegerArray routePoolCount = TicketingDS.TicketPoolCount.get(routenum - 1);
            //i表示发站，j表示到站,对于 90 表示从9站到10站
            for (int i = 1; i < stationnum; i++) {
                for (int j = i + 1; j <= stationnum; j++) {
                    if (!(j <= departure || i >= arrival)) {
                        int state = ((1 << (j - 1)) - 1) ^ ((1 << (i - 1)) - 1);
                        //原本有票
                        if ((stateBinary & state) == 0) {
                            //买票后票了 需要增加票数
                            if ((tempState & state) != 0) {
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
                }
            }
            stateBinary = tempState;
            return ticket;
        } else {
            return null;
        }
    }

    /**
     * 卖出一张票后更新余票数量
     *
     * @param route
     * @param departure
     * @param arrival
     */
    private synchronized void updateTicketPool(int route, int departure, int arrival) {
        AtomicIntegerArray routePoolCount = TicketingDS.TicketPoolCount.get(route - 1);
        int ticketState = ((1 << (arrival - 1)) - 1) ^ ((1 << (departure - 1)) - 1);
        int tempState = stateBinary | ticketState;
        //i表示发站，j表示到站,对于 90 表示从9站到10站
        for (int i = 1; i < stationnum; i++) {
            for (int j = i + 1; j <= stationnum; j++) {
                if (!(j <= departure || i >= arrival)) {
                    int state = ((1 << (j - 1)) - 1) ^ ((1 << (i - 1)) - 1);
                    //原本有票
                    if ((stateBinary & state) == 0) {
                        //买票后票了 需要增加票数
                        if ((tempState & state) != 0) {
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
            }
        }
    }

    /**
     * 该座位区间退票（已验证票的合法性，需要加锁）
     *
     * @return
     */
    public synchronized boolean refundTicket(Ticket ticket) {
        int departure = ticket.departure;
        int arrival = ticket.arrival;

        if (!inquiry(departure, arrival)) {
            //更新票池状态
            int ticketState = (stateFull >> (departure - 1) << (departure - 1)) ^ ((1 << (arrival - 1)) - 1);
            int tempState = stateBinary & ticketState;
            AtomicIntegerArray routePoolCount = TicketingDS.TicketPoolCount.get(ticket.route - 1);
            for (int i = 1; i < stationnum; i++) {
                for (int j = i + 1; j <= stationnum; j++) {
                    // 可能受影响的票
                    if (!(j <= departure || i >= arrival)) {
                        int state = ((1 << (j - 1)) - 1) ^ ((1 << (i - 1)) - 1);
                        //原本没票
                        if ((stateBinary & state) != 0) {
                            //退票后有票了 需要增加票数
                            if ((tempState & state) == 0) {
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
                }
            }
            // 将该区间设为空闲，可售票
            stateBinary = tempState;
            return true;
        }
        return false;
    }


    /**
     * 查询该座位是否还有票
     * 返回0，表示无票;否则返回1
     *
     * @param departure
     * @param arrival
     * @return
     */
    public synchronized boolean inquiry(int departure, int arrival) {
        int ticketState = ((1 << (arrival - 1)) - 1) ^ ((1 << (departure - 1)) - 1);
        if ((stateBinary & ticketState) == 0) {
            return true;
        } else {
            return false;
        }
    }
}
