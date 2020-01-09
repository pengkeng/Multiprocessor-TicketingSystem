package ticketingsystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLongArray;

public class Test {
    final static int threadnum = 96; // concurrent thread number
    final static int routenum = 20; // route is designed from 1 to 3
    final static int coachnum = 15; // coach is arranged from 1 to 5
    final static int seatnum = 100; // seat is allocated from 1 to 20
    final static int stationnum = 10; // station is designed from 1 to 5

    final static int testnum = 500000;
    final static int retpc = 5; // return ticket operation is 10% percent
    final static int buypc = 15; // buy ticket operation is 30% percent
    final static int inqpc = 80; //inquiry ticket operation is 60% percent
    static boolean run = false;

    static String passengerName() {
        Random rand = new Random();
        long uid = rand.nextInt(testnum);
        return "passenger" + uid;
    }

    public static void main(String[] args) throws InterruptedException {

        final TicketingDS tds = new TicketingDS(routenum, coachnum, seatnum, stationnum, threadnum);
        long[] spitTime = new long[]{0, 0, 0};
        long time1 = System.currentTimeMillis();
        int count = 10;
        for (int i = 0; i < count; i++) {
            long[] temps = test(tds);
            System.out.println(System.currentTimeMillis() - time1);
            time1 = System.currentTimeMillis();
            for (int j = 0; j < 3; j++) {
                spitTime[j] += temps[j];
            }
        }
        for (int i = 0; i < 3; i++) {
            spitTime[i] = spitTime[i] / count;
        }
        for (int i = 0; i < 3; i++) {
            System.out.println(spitTime[i] / 1000000);
        }
        long time = 0;
        for (int i = 0; i < 3; i++) {
            time += spitTime[i];
        }

//        int departure = 5;
//        int arrival = 10;
//        int ticketState = ((1 << (arrival - 1)) - 1) ^ ((1 << (departure - 1)) - 1);
//        int stateFull = (1 << stationnum) - 1;
//        System.out.println(Integer.toBinaryString(ticketState));
//        int state = Integer.parseInt("0000010100", 2);
//        System.out.println(Integer.toBinaryString(state));
//        state = state | ticketState;
//        System.out.println(Integer.toBinaryString(state));


//        int state = Integer.parseInt("1111110100", 2);
//        System.out.println(Integer.toBinaryString(state));
//        int refindticketState = (stateFull >> (departure - 1) << (departure - 1)) ^ ((1 << (arrival - 1)) - 1);
//        System.out.println(Integer.toBinaryString(refindticketState));
//        ;
//        int pre = state;
//        state = state & refindticketState;
//        System.out.println(Integer.toBinaryString(state));
//
//        int start = departure;
//        for (int i = departure - 1; i >= 1; i--) {
//            if (((state >> (i - 1)) & 1) == 1) {
//                break;
//            }
//            start = i;
//        }
//        int end = arrival - 1;
//        for (int i = arrival; i <= stationnum; i++) {
//            if (((state >> (i - 1)) & 1) == 1) {
//                break;
//            }
//            end = i;
//        }
//        if (end == 10) {
//            end = 9;
//        }
//
//        System.out.println(start + " " + end);
//        System.out.println("qqqq");
//        for (int i = start; i < arrival; i++) {
//            for (int j = departure + 1; j <= end + 1; j++) {
//                if (j > i) {
//                    System.out.println(i + " " + j);
//                }
//            }
//        }
//
//        System.out.println("qqqq");
//
//        for (int i = 1; i < arrival; i++) {
//            for (int j = departure + 1; j <= stationnum; j++) {
//                if (j > i) {
//                    int state1 = ((1 << (j - 1)) - 1) ^ ((1 << (i - 1)) - 1);
//                    //原本没票
//                    if ((pre & state1) != 0) {
//                        //退票后有票了 需要增加票数
//                        if ((state & state1) == 0) {
//                            System.out.println(i + " " + j);
//                        }
//                    }
//                }
//            }
//        }

//        int start = departure;
//        for (int i = departure - 1; i >= 1; i--) {
//            if (((state >> (i - 1)) & 1) == 1) {
//                break;
//            }
//            start = i;
//        }
//
//        int end = arrival - 1;
//        for (int i = arrival; i <= stationnum; i++) {
//            if (((state >> (i - 1)) & 1) == 1) {
//                break;
//            }
//            end = i;
//        }
//        System.out.println(start + " " + end);
//        for (int i = start; i < arrival; i++) {
//            for (int j = departure + 1; j <= end + 1; j++) {
//                System.out.println(i + " " + j);
//            }
//        }
    }


    private static long[] test(TicketingDS tds) throws InterruptedException {
//        AtomicLongArray threadbuy = new AtomicLongArray(100);
//        AtomicLongArray threadrefund = new AtomicLongArray(100);
//        AtomicLongArray threadinjury = new AtomicLongArray(100);
//        for (int i = 0; i < 100; i++) {
//            threadbuy.set(i, 0);
//            threadrefund.set(i, 0);
//            threadinjury.set(i, 0);
//        }
        Thread[] threads = new Thread[threadnum];
        for (int i = 0; i < threadnum; i++) {
            int finalI = i;
            threads[i] = new Thread(new Runnable() {
                public void run() {
                    Random rand = new Random();
                    Ticket ticket = new Ticket();
                    ArrayList<Ticket> soldTicket = new ArrayList<Ticket>();

                    //System.out.println(ThreadId.get());
                    for (int j = 0; j < testnum; j++) {
                        int sel = rand.nextInt(inqpc);
                        if (0 <= sel && sel < retpc && soldTicket.size() > 0) { // return ticket
                            int select = rand.nextInt(soldTicket.size());
                            if ((ticket = soldTicket.remove(select)) != null) {
//                                long time = System.nanoTime();
                                if (tds.refundTicket(ticket)) {
//                                    System.out.println("TicketRefund" + " \tpassenger" + ticket.passenger + " \t " + "route:" + ticket.route + " coach:" + ticket.coach + " departure:" + ticket.departure + "arrival: " + ticket.arrival + " seat:" + ticket.seat);
//                                    System.out.flush();
                                } else {
                                    System.out.println("ErrOfRefund1");
                                    System.out.flush();
                                }
//                                threadrefund.addAndGet(finalI, System.nanoTime() - time);
                            } else {
                                System.out.println("ErrOfRefund");
                                System.out.flush();
                            }
                        } else if (retpc <= sel && sel < buypc) { // buy ticket
                            String passenger = passengerName();
                            int route = rand.nextInt(routenum) + 1;
                            int departure = rand.nextInt(stationnum - 1) + 1;
                            int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
//                            long time = System.nanoTime();
                            if ((ticket = tds.buyTicket(passenger, route, departure, arrival)) != null) {
                                soldTicket.add(ticket);
//                                System.out.println("TicketBought" + " \tpassenger" + ticket.passenger + " \t " + "route:" + ticket.route + " coach:" + ticket.coach + " departure:" + ticket.departure + "arrival: " + ticket.arrival + " seat:" + ticket.seat + " " + route + " " + departure + " " + arrival);
//                                System.out.flush();
                            } else {
//                                System.out.println("TicketSoldOut" + " " + route + " " + departure + " " + arrival);
//                                System.out.flush();
                            }
//                            threadbuy.addAndGet(finalI, System.nanoTime() - time);
                        } else if (buypc <= sel && sel < inqpc) { // inquiry ticket
                            int route = rand.nextInt(routenum) + 1;
                            int departure = rand.nextInt(stationnum - 1) + 1;
                            int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
//                            long time = System.nanoTime();
                            int leftTicket = tds.inquiry(route, departure, arrival);
//                            System.out.println("RemainTicket" + " " + leftTicket + " " + route + " " + departure + " " + arrival);
//                            System.out.flush();
//                            threadinjury.addAndGet(finalI, System.nanoTime() - time);
                        }
                    }

                }
            });
            threads[i].start();
        }

        for (int i = 0; i < threadnum; i++) {
            threads[i].join();
        }

        long[] times = new long[]{0, 0, 0};
//        for (int i = 0; i < 100; i++) {
//            times[0] += threadbuy.get(i);
//            times[1] += threadrefund.get(i);
//            times[2] += threadinjury.get(i);
//        }
        return times;
    }
}

