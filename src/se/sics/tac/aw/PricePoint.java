package se.sics.tac.aw;

/**
 * Price point for hotel/flight/entertainment
 */
public class PricePoint {

    /**
     * Ask Price.
     */
    public float value;

    /**
     * Seconds elapsed since the start of the game.
     */
    public long seconds;

    public PricePoint(float value, long seconds){
        this.value = value;
        this.seconds = seconds;
    }
}
