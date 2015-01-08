package se.sics.tac.aw;

/**
 * Created by Noureldien on 12/2/2014.
 */
public class HotelAuctionHistory {

    public int auction;

    public int allocation;

    public int own;

    public float bidPrice;

    public float askPrice;

    public  HotelAuctionHistory(int auction, int allocation, int own, float bidPrice, float askPrice){

        this.auction = auction;
        this.allocation = allocation;
        this.own = own;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
    }
}
