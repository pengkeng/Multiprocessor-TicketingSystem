package ticketingsystem;

public class TicketingDS implements TicketingSystem {

    private int threadnum;
    private int routenum;
    private int coachnum;
    private int seatnum;
    private int stationnum;
    private int[] TicketPool;

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
        TicketPool = new int[routenum * coachnum * seatnum * stationnum];
    }

    @Override
    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {
        return null;
    }

    @Override
    public int inquiry(int route, int departure, int arrival) {
        return 0;
    }

    @Override
    public boolean refundTicket(Ticket ticket) {
        return false;
    }

    //ToDo

}
