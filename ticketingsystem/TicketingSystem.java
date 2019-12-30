package ticketingsystem;

class Ticket {


    long tid;
    String passenger;
    int route;
    int coach;
    int seat;
    int departure;
    int arrival;

    Ticket() {

    }

    Ticket(String passenger, int route, int coach, int seat, int departure, int arrival) {
        this.passenger = passenger;
        this.route = route;
        this.coach = coach;
        this.seat = seat;
        this.departure = departure;
        this.arrival = arrival;
        this.tid = route * 10000 + coach * 1000 + seat * 100 + departure * 10 + arrival;
    }

    /**
     * 判断票据是否合法
     * @return
     */
    public boolean isValid() {
        if (route * 10000 + coach * 1000 + seat * 100 + departure * 10 + arrival == tid) {
            return true;
        } else {
            return false;
        }
    }
}


public interface TicketingSystem {

    /**
     * 买票
     *
     * @param passenger
     * @param route
     * @param departure
     * @param arrival
     * @return
     */
    Ticket buyTicket(String passenger, int route, int departure, int arrival);

    /**
     * 查询
     *
     * @param route
     * @param departure
     * @param arrival
     * @return
     */
    int inquiry(int route, int departure, int arrival);

    /**
     * 退票
     *
     * @param ticket
     * @return
     */
    boolean refundTicket(Ticket ticket);
}
