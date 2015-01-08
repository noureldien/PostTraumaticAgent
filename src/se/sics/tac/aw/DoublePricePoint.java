package se.sics.tac.aw;

/**
 * Price point for hotel/flight/entertainment
 */
public class DoublePricePoint {

    /**
     * Ask Price.
     */
    public float ask;

    /**
     * Bid Price.
     */
    public float bid;

    /**
     * Seconds elapsed since the start of the game.
     */
    public long seconds;

    public DoublePricePoint(float ask, float bid, long seconds){
        this.ask = ask;
        this.bid = bid;
        this.seconds = seconds;
    }
}
