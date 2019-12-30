package ticketingsystem;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

public class TicketingDS implements TicketingSystem {

    private int threadnum;
    private int routenum;
    private int coachnum;
    private int seatnum;
    private int stationnum;//最大为10
    private ArrayList<ArrayList<Seat>> TicketPool = new ArrayList<>();
    private ArrayList<CopyOnWriteArrayList<Integer>> TicketPoolCount = new ArrayList<>();//该数据结构使用 ReentrantLock, 写加锁，读不加锁

    /**
     * 每条路线的座位数 coachnum*seatnum
     */
    private int count;

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
            ArrayList<Seat> routePool = new ArrayList<>();
            for (int j = 0; j < this.coachnum; j++) {
                for (int k = 0; k < seatnum; k++) {
                    Seat seat = new Seat(i + 1, j + 1, k + 1, this.stationnum);
                    routePool.add(seat);
                }
            }
            CopyOnWriteArrayList<Integer> routePoolCount = new CopyOnWriteArrayList<>();
            for (int j = 0; j < 100; j++) {
                routePoolCount.add(count);
            }
            TicketPoolCount.add(routePoolCount);
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

        if (inquiry(route, departure, arrival) == 0) {
            return null;
        }
        //拿到对应路线
        ArrayList<Seat> routePool = TicketPool.get(route - 1);
        //每次随机一个座位序号，进行买票
        //对所有位置进行遍历
        for (int i = 0; i < count; i++) {
            Seat seat = routePool.get(i);
            if (!seat.isFull) {
                Ticket ticket = seat.buyTicket(passenger, departure, arrival);
                if (ticket != null) {
                    updateTicketPool(route, departure, arrival);
                    return ticket;
                }
            }
        }
        return null;
    }

    /**
     * 卖出一张票后更新余票数量
     *
     * @param route
     * @param departure
     * @param arrival
     */
    private void updateTicketPool(int route, int departure, int arrival) {
        CopyOnWriteArrayList<Integer> routePoolCount = TicketPoolCount.get(route - 1);
        //i表示发站，j表示到站,对于 90 表示从9站到10站
        for (int i = 1; i < departure; i++) {
            for (int j = i + 1; j <= departure; j++) {
                int index;
                if (j == 10) {
                    index = i * 10;
                } else {
                    index = i * 10 + j;
                }
                int num = routePoolCount.get(index) - 1;
                routePoolCount.set(index, num);
            }
        }
        for (int k = arrival; k < stationnum; k++) {
            for (int l = k + 1; l <= stationnum; l++) {
                int index;
                if (l == 10) {
                    index = k * 10;
                } else {
                    index = k * 10 + l;
                }
                int num = routePoolCount.get(index) - 1;
                routePoolCount.set(index, num);
            }
        }
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
        arrival = arrival % stationnum;
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
        int route = ticket.route;
        int coachId = ticket.coach;
        int seatId = ticket.seat;
        int index = (coachId - 1) * seatnum + seatId - 1;
        Seat seat = TicketPool.get(route - 1).get(index);
        if (ticket.isValid()) {
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
    private int stateBinary;
    private int stateFull;
    public boolean isFull = false;

    Seat(int route, int coach, int seat, int station) {
        this.routenum = route;
        this.coachnum = coach;
        this.seatnum = seat;
        this.stationnum = station;
        stateBinary = 0;

        stateFull = 1 << this.stationnum - 1;
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
            Ticket ticket = new Ticket(passenger, routenum, coachnum, seatnum, departure, arrival);
            // 置位当前区间已无票
            stateBinary = stateBinary | ticketState;
            if ((stateBinary ^ stateFull) == 0 || (stateBinary ^ (stateFull >> 1)) == 0) {
                isFull = true;
            }
            return ticket;
        } else {
            return null;
        }
    }

    /**
     * 该座位区间退票（已验证票的合法性，其他线程不会影响退票的正确性）
     *
     * @return
     */
    public boolean refundTicket(Ticket ticket) {
        isFull = false;
        int departure = ticket.departure;
        int arrival = ticket.arrival;
        // 将该区间设为空闲，可售票
        int ticketState = (stateFull >> (departure - 1) << (departure - 1)) | ((1 << (arrival - 1)) - 1);
        stateBinary = stateBinary & ticketState;
        return true;
    }


    /**
     * 查询票，可以多个对象同时操作，允许误差存在
     * 返回0，表示无票;否则返回1
     *
     * @param departure
     * @param arrival
     * @return
     */
    public int inquiry(int departure, int arrival) {

        //该座位已无余票
        if (isFull) {
            return 0;
        }
        int ticketState = ((1 << (arrival - 1)) - 1) ^ ((1 << (departure - 1)) - 1);
        if ((stateBinary & ticketState) == 0) {
            return 1;
        } else {
            return 0;
        }
    }
}
