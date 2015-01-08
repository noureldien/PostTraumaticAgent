/**
 * TAC AgentWare
 * http://www.sics.se/tac        tac-dev@sics.se
 *
 * Copyright (c) 2001-2005 SICS AB. All rights reserved.
 *
 * SICS grants you the right to use, modify, and redistribute this
 * software for noncommercial purposes, on the conditions that you:
 * (1) retain the original headers, including the copyright notice and
 * this text, (2) clearly document the difference between any derived
 * software and the original, and (3) acknowledge your use of this
 * software in pertaining publications and reports.  SICS provides
 * this software "as is", without any warranty of any kind.  IN NO
 * EVENT SHALL SICS BE LIABLE FOR ANY DIRECT, SPECIAL OR INDIRECT,
 * PUNITIVE, INCIDENTAL OR CONSEQUENTIAL LOSSES OR DAMAGES ARISING OUT
 * OF THE USE OF THE SOFTWARE.
 *
 * -----------------------------------------------------------------
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 23 April, 2002
 * Updated : $Date: 2005/06/07 19:06:16 $
 *	     $Revision: 1.1 $
 * ---------------------------------------------------------
 * PostTraumaticAgent is a simplest possible agent for TAC. It uses
 * the TACAgent agent ware to interact with the TAC server.
 *
 * Important methods in TACAgent:
 *
 * Retrieving information about the current Game
 * ---------------------------------------------
 * int getGameID()
 *  - returns the id of current game or -1 if no game is currently plaing
 *
 * getServerTime()
 *  - returns the current server time in milliseconds
 *
 * getGameTime()
 *  - returns the time from start of game in milliseconds
 *
 * getGameTimeLeft()
 *  - returns the time left in the game in milliseconds
 *
 * getGameLength()
 *  - returns the game length in milliseconds
 *
 * int getAuctionNo()
 *  - returns the number of auctions in TAC
 *
 * int getClientPreference(int client, int type)
 *  - returns the clients preference for the specified type
 *   (types are TACAgent.{ARRIVAL, DEPARTURE, HOTEL_VALUE, E1, E2, E3}
 *
 * int getAuctionFor(int category, int type, int day)
 *  - returns the auction-id for the requested resource
 *   (categories are TACAgent.{CAT_FLIGHT, CAT_HOTEL, CAT_ENTERTAINMENT
 *    and types are TACAgent.TYPE_INFLIGHT, TACAgent.TYPE_OUTFLIGHT, etc)
 *
 * int getAuctionCategory(int auction)
 *  - returns the category for this auction (CAT_FLIGHT, CAT_HOTEL,
 *    CAT_ENTERTAINMENT)
 *
 * int getAuctionDay(int auction)
 *  - returns the day for this auction.
 *
 * int getAuctionType(int auction)
 *  - returns the type for this auction (TYPE_INFLIGHT, TYPE_OUTFLIGHT, etc).
 *
 * int getOwn(int auction)
 *  - returns the number of items that the agent own for this
 *    auction
 *
 * Submitting Bids
 * ---------------------------------------------
 * void submitBid(Bid)
 *  - submits a bid to the tac server
 *
 * void replaceBid(OldBid, Bid)
 *  - replaces the old bid (the current active bid) in the tac server
 *
 *   Bids have the following important methods:
 *    - create a bid with new Bid(AuctionID)
 *
 *   void addBidPoint(int quantity, float price)
 *    - adds a bid point in the bid
 *
 * Help methods for remembering what to buy for each auction:
 * ----------------------------------------------------------
 * int getAllocation(int auctionID)
 *   - returns the allocation set for this auction
 * void setAllocation(int auctionID, int quantity)
 *   - set the allocation for this auction
 *
 *
 * Callbacks from the TACAgent (caused via interaction with server)
 *
 * bidUpdated(Bid bid)
 *  - there are TACAgent have received an answer on a bid query/submission
 *   (new information about the bid is available)
 *
 * bidRejected(Bid bid)
 *  - the bid has been rejected (reason is bid.getRejectReason())
 *
 * bidError(Bid bid, int error)
 *  - the bid contained errors (error represent error status - commandStatus)
 *
 * quoteUpdated(Quote quote)
 *  - new information about the quotes on the auction (quote.getAuction())
 *    has arrived
 *
 * quoteUpdated(int category)
 *  - new information about the quotes on all auctions for the auction
 *    category has arrived (quotes for a specific type of auctions are
 *    often requested at once).

 * auctionClosed(int auction)
 *  - the auction with id "auction" has closed
 *
 * transaction(Transaction transaction)
 *  - there has been a transaction
 *
 * gameStarted()
 *  - a TAC game has started, and all information about the
 *    game is available (preferences etc).
 *
 * gameStopped()
 *  - the current game has ended
 *
 */

package se.sics.tac.aw;

import com.sun.org.apache.xalan.internal.extensions.ExpressionContext;
import se.sics.tac.util.ArgEnumerator;

import javax.rmi.CORBA.Util;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.*;

public class PostTraumaticAgent extends AgentImpl {

    // region Private Enums

    private enum HotelAuctionBiddingMode {
        Normal,
        Final,
    }

    // endregion Private Enums

    // region Private Constants

    /**
     * Logger. Log information regarding the processes that are happening while the agent is running.
     */
    private static final Logger log = Logger.getLogger(PostTraumaticAgent.class.getName());

    /**
     * If in the debug mode or not.
     */
    private static final boolean DEBUG = false;

    private static String FlightLogPrefix = "************* ";
    private static String HotelLogPrefix = "------------- ";
    private static String EntertainmentLogPrefix = "+++++++++++++ ";
    private static String LogPrefix = "              ";

    // endregion Private Constants

    // region Private Variables

    /**
     * Array of prices for the last buy-bid in each auction. Note that it is a list of 28 items for
     * the 28 auctions I can buy from.
     */
    private float[] buyBidPrices;
    /**
     * Array of prices for the last buy-bid in each entertainment auction. Note that it is a list
     * of only 12 items for the 12 entertainment auctions I can sell to.
     */
    private float[] sellBidPrices;
    /**
     * List of prices, each item in the list contains 8 prices for 8 flight auctions.
     * The list will eventually contains 6*9 = 54 items which means prices vary each 10 seconds for 9 mins.
     */
    private ArrayList<ArrayList<PricePoint>> flightPrices;
    /**
     * Array of prices for 8 auctions of the hotels. Each item in the array contains all the prices asked in this auction.
     */
    private ArrayList<ArrayList<PricePoint>> hotelPrices;
    /**
     * Array of prices for 12 auctions of the . Each item in the array contains all the prices asked in this auction.
     */
    private ArrayList<ArrayList<DoublePricePoint>> entertainmentPrices;
    /**
     * Client preferences.
     */
    private int[][] clientPreferences;
    /**
     * List of closed hotel auctions.
     */
    private ArrayList<Integer> closedHotelAuctions;
    /**
     * Data of the hotel auctions participated in, these data are collected when an auction closes.
     */
    private ArrayList<HotelAuctionHistory> hotelAuctionsHistory;
    /**
     * If the objects used in the game are initialized and ready.
     */
    private boolean gameInitialized;
    /**
     * Timer to call the last-second bidding strategy for the hotel auction.
     */
    private Timer hotelAuctionsTimer;
    /**
     * Timer to continuously all the entertainment auctions processor.
     */
    private Timer entertainmentAuctionsTimer;
    /**
     * List of allocations in the valid entertainment bids the agents submits and are still waiting for clearing on the server.
     */
    private int[] entertainmentBidsAllocations;
    /**
     * List of unwanted entertainment owns and will be offered for sell.
     */
    private int[] entertainmentDeAllocations;
    /**
     * List of de-allocations in the valid entertainment bids the agents submits and are still waiting for clearing on the server.
     */
    private int[] entertainmentBidsDeAllocations;
    /**
     * The complete table of the clients' entertainment auctions
     * after assessing the initial owns at the beginning of the game
     * and figuring out what is the suitable configuration of the entertainment allocations to satisfy:
     * - De-Allocate the least amount of initial owns
     * - Satisfy the client preferences
     * It is a list of 8, each one contains list of auctions of clients preferred entertainment tickets
     */
    private ArrayList<ArrayList<Integer>> clientPreferredEntertainmentAuctions;
    /**
     * Bidding mode of the hotel auction.
     */
    private HotelAuctionBiddingMode hotelBiddingMode;
    /**
     * Estimated demand on flight auctions.
     */
    private float[] flightAuctionDemand;
    /**
     * Estimated demand on hotel auctions.
     */
    private float[] hotelAuctionDemand;
    /**
     * Initial allocations of the hotel auctions.
     */
    private int[] hotelInitialAllocations;
    /**
     * If the flight and hotel estimated demands are calculated or not.
     */
    private boolean isEstimatedDemandCalculated;
    /**
     * Are the entertainment auction initialized or not.
     */
    boolean areEntertainmentAuctionsInitialized = false;

    // endregion private variables

    // region public methods

    /**
     * Only for backward compatibility
     */
    public static void main(String[] args) {

        TACAgent.main(args);
    }

    /**
     * Called once the agent is created for the first time.
     * Define all your variables and objects.
     *
     * @param args an <code>ArgEnumerator</code> value containing any
     */
    protected void init(ArgEnumerator args) {

    }

    public void gameStarted() {

        log.fine(LogPrefix + "Game " + agent.getGameID() + " started!");

        // initialize all objects/arrays required
        gameInitialized = false;
        initializeVariables();
        initializeHotelAuctionsTimer();
        initializeEntertainmentAuctionsTimer();
        gameInitialized = true;

        // setting the allocations must be called after initialize()
        // neither flight nor the entertainment have their allocations
        // set at the beginning of the game, both of them have their
        // allocations periodically processed in an independent way
        // only flight/entertainment allocations are added for clients
        // with hotel rooms
        hotelSetAllocations();

        // print the initial client preferences
        printClientPreferences();

        // remove un-needed owns form the entertainment
        entertainmentDeAllocator();

        // must be called after the allocations were set
        hotelAuctionsProcessor();
    }

    public void gameStopped() {

        entertainmentAuctionsTimer.stop();
        flightPrintPrices();
        hotelPrintPrices();
        entertainmentPrintPrices();
        printClientPreferences();
        flightPredictionResults();
        hotelPredictionResults();

        log.fine(LogPrefix + "Game Stopped!");
    }

    public void quoteUpdated(Quote quote) {

        if (!gameInitialized) {
            return;
        }

        int auction = quote.getAuction();
        int auctionCategory = agent.getAuctionCategory(auction);
        switch (auctionCategory) {

            case TACAgent.CAT_FLIGHT:
                // log.fine(LogPrefix + "A flight quote for " + agent.getAuctionTypeAsString(auction) + " has been updated");
                break;

            case TACAgent.CAT_HOTEL:
                hotelPricesCollector(quote);
                hotelQuoteUpdated(quote);
                // log.fine(LogPrefix + "A hotel quote for " + agent.getAuctionTypeAsString(auction) + " has been updated");
                break;

            case TACAgent.CAT_ENTERTAINMENT:
                entertainmentPricesCollector(quote);
                entertainmentQuoteUpdated(quote);
                // log.fine(LogPrefix + "An entertainment quote for " + agent.getAuctionTypeAsString(auction) + " has been updated");
                break;

            default:
                break;
        }
    }

    public void quoteUpdated(int auctionCategory) {

        if (!gameInitialized) {
            return;
        }

        // log.fine(LogPrefix + "All quotes for " + agent.auctionCategoryToString(auctionCategory) + " has been updated");

        switch (auctionCategory) {

            case TACAgent.CAT_FLIGHT:
                // this needs to be called in the same sequence
                flightPricesCollector();
                if (!isEstimatedDemandCalculated && flightPrices.get(0).size() < 8) {
                    initializeEstimatedDemand();
                    isEstimatedDemandCalculated = true;
                }
                flightQuoteUpdated();
                break;

            case TACAgent.CAT_HOTEL:
                break;

            case TACAgent.CAT_ENTERTAINMENT:
                break;

            default:
                break;
        }
    }

    public void bidUpdated(Bid bid) {

        if (!gameInitialized) {
            return;
        }

        int auctionCategory = agent.getAuctionCategory(bid.getAuction());
        switch (auctionCategory) {

            case TACAgent.CAT_FLIGHT:
                //log.fine(FlightLogPrefix + "Bid Updated: id=" + bid.getID() + " auction=" + bid.getAuction() + " state=" + bid.getProcessingStateAsString());
                break;

            case TACAgent.CAT_HOTEL:
                //log.fine(HotelLogPrefix + "Bid Updated: id=" + bid.getID() + " auction=" + bid.getAuction() + " state=" + bid.getProcessingStateAsString());
                break;

            case TACAgent.CAT_ENTERTAINMENT:
                entertainmentBidUpdated(bid);
                //log.fine(EntertainmentLogPrefix + "Bid Updated: id=" + bid.getID() + " auction=" + bid.getAuction() + " state=" + bid.getProcessingStateAsString());
                break;

            default:
                break;
        }
    }

    public void bidRejected(Bid bid) {

        if (!gameInitialized) {
            return;
        }

        int auction = bid.getAuction();
        Quote quote = agent.getQuote(auction);
        int quantity = bid.getQuantity();

        // check if bid is sell/buy bid according to the quantity
        if (quantity >= 0) {
            log.warning("!!!!!!!! Buy Bid Rejected:");
            log.warning("!!!!!!!!               ID: " + bid.getID());
            log.warning("!!!!!!!!           Reason: " + +bid.getRejectReason() + " (" + bid.getRejectReasonAsString() + ')');
            log.warning("!!!!!!!!          Auction: " + agent.getAuctionTypeAsString(auction));
            log.warning("!!!!!!!!         My Price: " + buyBidPrices[auction]);
            log.warning("!!!!!!!!        Ask Price: " + quote.getAskPrice());
        } else {
            int reason = bid.getRejectReason();
            String logMessage = "!!!!!!!! Sell Bid Rejected with reason: " + +bid.getRejectReason() + " (" + bid.getRejectReasonAsString() + ')';
            if (reason == Bid.ACTIVE_BID_CHANGED) {
                log.fine(logMessage);
            } else {
                log.warning(logMessage);
            }
        }
    }

    public void bidError(Bid bid, int status) {

        log.warning("!!!!!!!! Bid Error in auction " + bid.getAuction() + ": " + status + " (" + agent.commandStatusToString(status) + ')');

    }

    public void transaction(Transaction transaction) {
        //log.fine(LogPrefix + "Transaction " + transaction.toString() + " received!");
    }

    public void auctionClosed(int auction) {

        // please note that this method is worthless, because it's way
        // to slow that knowing if an auction is closed or not by checking
        // its quote in quoteUpdated()
        // that means when hotel auction is closed, the method quoteUpdated()
        // is called first then the method auctionClosed() is called later
        // and there is a delay about 10 ms between them

        int auctionCategory = agent.getAuctionCategory(auction);
        switch (auctionCategory) {

            case TACAgent.CAT_FLIGHT:
                break;

            case TACAgent.CAT_HOTEL:
                hotelAuctionClosed(auction);
                break;

            case TACAgent.CAT_ENTERTAINMENT:
                break;

            default:
                break;
        }
    }

    // endregion Public Methods

    // region Private Methods [Initialize]

    /**
     * Initialize/reset all objects/variables used in the game.
     */
    private void initializeVariables() {

        // initialize arrays to collect prices of flight/hotel/entertainment auctions
        int count = 8;
        flightPrices = new ArrayList<ArrayList<PricePoint>>(count);
        for (int i = 0; i < count; i++) {
            flightPrices.add(new ArrayList<PricePoint>());
        }
        count = 8;
        hotelPrices = new ArrayList<ArrayList<PricePoint>>(count);
        for (int i = 0; i < count; i++) {
            hotelPrices.add(new ArrayList<PricePoint>());
        }
        count = 12;
        entertainmentPrices = new ArrayList<ArrayList<DoublePricePoint>>(count);
        for (int i = 0; i < count; i++) {
            entertainmentPrices.add(new ArrayList<DoublePricePoint>());
        }
        count = 8;
        clientPreferredEntertainmentAuctions = new ArrayList<ArrayList<Integer>>(count);
        for (int i = 0; i < count; i++) {
            clientPreferredEntertainmentAuctions.add(new ArrayList<Integer>());
        }

        entertainmentBidsAllocations = new int[12];
        entertainmentDeAllocations = new int[12];
        entertainmentBidsDeAllocations = new int[12];

        log.fine(LogPrefix + "Auction Number: " + agent.getAuctionNo());

        buyBidPrices = new float[28];
        sellBidPrices = new float[12];
        closedHotelAuctions = new ArrayList<Integer>();
        hotelAuctionsHistory = new ArrayList<HotelAuctionHistory>();
        clientPreferences = agent.cloneClientPreferences();
        hotelInitialAllocations = new int[8];

        hotelBiddingMode = HotelAuctionBiddingMode.Normal;
        flightAuctionDemand = null;
        hotelAuctionDemand = null;
        isEstimatedDemandCalculated = false;
        areEntertainmentAuctionsInitialized = false;
    }

    /**
     * Initialize the timer to monitor hotel auction. This timer calls
     * the last-second bidding strategy for a hotels auctions just second(s) before close.
     */
    private void initializeHotelAuctionsTimer() {

        // float averageResponseTime = TACMessage.getAverageResponseTime();

        int timeInterval = 2 * 1000;
        ActionListener actionListener = (ActionEvent event) -> {
            hotelAuctionsTimerTick();
        };
        hotelAuctionsTimer = new Timer(timeInterval, actionListener);
        hotelAuctionsTimer.start();
    }

    /**
     * Initialize the timer to continuously call the entertainment auction processor.
     */
    private void initializeEntertainmentAuctionsTimer() {

        int timeInterval = 5 * 1000;
        ActionListener actionListener = (ActionEvent event) -> {
            // if entertainment auctions are ready, call the timer ticker
            // if not, update the readiness status
            if (areEntertainmentAuctionsInitialized) {
                entertainmentAuctionsTimerTick();
            } else {
                areEntertainmentAuctionsInitialized = true;
                int auction;
                int auctionStatus;
                Quote quote;
                for (int i = 0; i < 12 && areEntertainmentAuctionsInitialized; i++) {
                    auction = i + 16;
                    quote = agent.getQuote(auction);
                    auctionStatus = quote.getAuctionStatus();
                    areEntertainmentAuctionsInitialized = isAuctionClear(auctionStatus);
                }
            }
        };
        entertainmentAuctionsTimer = new Timer(timeInterval, actionListener);
        entertainmentAuctionsTimer.start();
    }

    /**
     * Calculate the estimated demand on flight and hotel auctions.
     */
    private void initializeEstimatedDemand() {

        float[] initialInFlightPrices = new float[4];
        float[] initialOutFlightPrices = new float[4];
        for (int i = 0; i < 4; i++) {
            initialInFlightPrices[i] = flightPrices.get(i).get(0).value;
            initialOutFlightPrices[i] = flightPrices.get(i + 4).get(0).value;
        }

        DemandEstimator demandEstimator = new DemandEstimator(initialInFlightPrices, initialOutFlightPrices);
        flightAuctionDemand = demandEstimator.flightDemands();
        hotelAuctionDemand = demandEstimator.hotelDemands();

        log.fine(HotelLogPrefix + "Flight Demand Estimations: " + Arrays.toString(flightAuctionDemand));
        log.fine(HotelLogPrefix + "Hotel Demand Estimations: " + Arrays.toString(hotelAuctionDemand));
    }

    // endregion Private Methods [Initialize]

    // region Private Methods [Flight]

    /**
     * Process the updated flight quotes and see if a bid is needed or not.
     */
    private void flightQuoteUpdated() {

        if (closedHotelAuctions.size() < 8) {
            flightAllocationsProcessor();
        } else {
            flightAllocationsFinalProcessor();
        }
        flightAuctionsProcessor();
    }

    /**
     * Process the updated flight quotes and see if a bid is need to be updated/added or not.
     * The strategy for the flight auctions (i.e adding new flight allocations) is built on 2 factors
     * 1. buy flight tickets only after the hotel tickets are bought
     * it doesn't matter if the auction is closed or not
     * also it doesn't matter if we reserve a hotel ticket for the other days of the trip
     * because as long as we're a hotel ticket, we can make a feasible travel package
     * 2. before buying a flight ticket, predict the future minimum price
     * if it is the current auction cycle, buy the ticket,
     * if not wait until the appropriate cycle.
     */
    private void flightAllocationsProcessor() {

        // the additional allocations for ticket we want to buy for each flight auction
        // additional allocation after assessing the current situation of bought hotels
        // we may have allocation from previously assessed hotel situations
        int[] flightAllocations = new int[8];

        // get all the owned flight tickets
        // practically, own hear means agent.getOwn() + agent.getAllocation()
        // because we might have added flight allocations in a previous flightAuctionProcessing
        int[] flightOwns = new int[8];
        for (int i = 0; i < 8; i++) {
            flightOwns[i] = agent.getOwn(i) + agent.getAllocation(i);
        }
        log.fine(FlightLogPrefix + "Flight Owns: " + Arrays.toString(flightOwns));

        // the already owned hotel rooms
        int[] hotelOwns = new int[8];
        for (int i = 0; i < 8; i++) {
            hotelOwns[i] = agent.getOwn(i + 8);
        }
        log.fine(FlightLogPrefix + "Hotel Owns: " + Arrays.toString(hotelOwns));

        int firstHotelDay;
        int lastHotelDay;
        int hotelType;
        int hotelIndexOffset;
        boolean isFirstDay;
        boolean isLastDay;
        boolean isCompleteStay;

        // loop on all clients
        for (int i = 0; i < 8; i++) {

            // get the preferences of the hotels get only
            // the first day to stay in the hotel (arrival day)
            // the last day to stay in the hotel (the day before the departure day)
            firstHotelDay = clientPreferences[i][TACAgent.ARRIVAL];
            lastHotelDay = clientPreferences[i][TACAgent.DEPARTURE] - 1;
            //log.fine( FlightLogPrefix + "Client " + (i + 1) + " hotel first/last days: " + firstHotelDay + ", " + lastHotelDay);

            // then check if for these preference days, a hotel room was owned
            hotelType = getHotelType(i);
            hotelIndexOffset = hotelType == TACAgent.TYPE_CHEAP_HOTEL ? 0 : 4;
            isFirstDay = hotelOwns[hotelIndexOffset + firstHotelDay - 1] > 0;
            isLastDay = hotelOwns[hotelIndexOffset + lastHotelDay - 1] > 0;
            if (isFirstDay || isLastDay) {

                // check if all the hotel night(s) for the whole stay/trip is(are) reserved
                isCompleteStay = true;
                for (int j = firstHotelDay; j <= lastHotelDay; j++) {
                    isCompleteStay = isCompleteStay && hotelOwns[hotelIndexOffset + j - 1] > 0;
                }

                // now if client has first and second day but not the whole (complete) stay
                // we've to either choose the first of the last day
                // according to what? according to the estimated flight demand
                // I don't have a strong reason why I picked up either the fist/last day depending on the flight demand
                // while I have good reasons for not buying both first and last day flight tickets
                // for the client without a complete stay yet (simply he might loose a middle day in the future)
                // to decide weather to consider the first or the last, try to choose which ticket
                // is likely to decrease in the future
                if (isFirstDay && isLastDay && !isCompleteStay) {

                    log.fine(FlightLogPrefix + "This client has not complete stay but he has first and last hotel days reserved: " + (i + 1));
                    log.fine(FlightLogPrefix + "First/Last Hotel Days: " + firstHotelDay + ", " + lastHotelDay);

                    FlightPricePredictor predictor;
                    double[] prices;
                    boolean firstDayShouldBuy;
                    boolean lastDayShouldBuy;

                    int firstDayAuction = agent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_INFLIGHT, firstHotelDay);
                    log.fine(FlightLogPrefix + "First-Day Flight Auction: " + agent.getAuctionTypeAsString(firstDayAuction));
                    prices = listToArray(flightPrices.get(firstDayAuction));
                    predictor = new FlightPricePredictor(prices);
                    firstDayShouldBuy = predictor.shouldBuy();

                    int lastDayAuction = agent.getAuctionFor(TACAgent.CAT_FLIGHT, TACAgent.TYPE_OUTFLIGHT, lastHotelDay + 1);
                    log.fine(FlightLogPrefix + "Last-Day Flight Auction: " + agent.getAuctionTypeAsString(lastDayAuction));
                    prices = listToArray(flightPrices.get(lastDayAuction));
                    predictor = new FlightPricePredictor(prices);
                    lastDayShouldBuy = predictor.shouldBuy();

                    // if both same recommendation, then consider only the cheapest of them
                    // else, consider the one recommended to buy
                    float firstDayPrice = agent.getQuote(firstDayAuction).getAskPrice();
                    float lastDayPrice = agent.getQuote(lastDayAuction).getAskPrice();
                    if (firstDayShouldBuy == lastDayShouldBuy) {
                        if (firstDayPrice < lastDayPrice) {
                            isFirstDay = true;
                            isLastDay = false;
                        } else {
                            isFirstDay = false;
                            isLastDay = true;
                        }
                    } else {
                        isFirstDay = firstDayShouldBuy;
                        isLastDay = lastDayShouldBuy;
                    }

                    log.fine(FlightLogPrefix + "Should buy for first and last day? " + (firstDayShouldBuy) + ", " + (lastDayShouldBuy));
                    log.fine(FlightLogPrefix + "So, we're consider first/last day? " + (isFirstDay) + ", " + (isLastDay));
                }

                if (isFirstDay || isCompleteStay) {

                    // if we don't own an in-flight, add one to the new allocations
                    // else, remove from own
                    if (flightOwns[firstHotelDay - 1] < 1) {
                        flightAllocations[firstHotelDay - 1]++;
                    } else {
                        flightOwns[firstHotelDay - 1]--;
                    }
                    // remove the first-day hotel room from the owns
                    // because we've (found a flight to it/allocated a flight to it)
                    hotelOwns[hotelIndexOffset + firstHotelDay - 1]--;
                }

                // now if flight needed
                // - hotel stay is 2 days or less
                // - hotel stay is more than 2 days with all days have hotels
                // then go either subtract from own flights or add new allocation
                if (isLastDay || isCompleteStay) {

                    // if we don't own an out-flight, add one to the new allocations
                    // else, remove from own
                    if (flightOwns[lastHotelDay - 1 + 4] < 1) {
                        flightAllocations[lastHotelDay - 1 + 4]++;
                    } else {
                        flightOwns[lastHotelDay - 1 + 4]--;
                    }

                    // remove the last-day hotel room from the owns
                    // because we've (found a flight to it/allocated a flight to it)
                    hotelOwns[hotelIndexOffset + lastHotelDay - 1]--;
                }
            }
        }

        // finally, add the calculated allocations from the current processing
        // to the whole allocations of the agent
        // allocating a flight does not mean bidding on it immediately
        // an agent.flightAllocation then enters the step of flightBidProcessor
        // which bids on the suitable time (time which a flight auction is predicted to have the min price)
        int allocation;
        log.fine(FlightLogPrefix + "New Allocations after Processing: " + Arrays.toString(flightAllocations));
        for (int i = 0; i < 8; i++) {
            allocation = agent.getAllocation(i) + flightAllocations[i];
            agent.setAllocation(i, allocation);
        }
    }

    /**
     * This is the last allocation processing for the flight auctions.
     * It is called over the last minute of the game. We want simply to process
     * the allocations but with slight change than the typical flightAllocationsProcessor()
     * Instead of looking at the preferred arrival/departure dates of a client and
     * see if a flight needed we'd
     * - look at the client who has not an arrival/departure flight
     * - look at the max adjacent number of hotels rooms he have
     * - then allocate arrival/departure flights at the beginning/end of these rooms
     * - the main goal here is to complete any feasible package for a client who hasn't yet
     */
    private void flightAllocationsFinalProcessor() {

        int firstHotelDay;
        int lastHotelDay;
        int hotelType;
        int hotelIndexOffset;
        boolean isCompleteHotelStay;
        int client;

        // region Collect Data

        // list of clients, because we want to get rid first
        // of clients who have feasible
        ArrayList<Integer> clients = new ArrayList<>();
        clients.addAll(Arrays.asList(new Integer[]{0, 1, 2, 3, 4, 5, 6, 7}));

        // the additional allocations for ticket we want to buy for each flight auction
        // additional allocation after assessing the current situation of bought hotels
        // we may have allocation from previously assessed hotel situations
        int[] flightAllocations = new int[8];

        // get all the owned hotel rooms
        // piratically, own hear means agent.getOwn() + agent.getAllocation()
        // because we might have added flight allocations in a previous flightAuctionProcessing
        int[] flightOwns = new int[8];
        for (int i = 0; i < 8; i++) {
            flightOwns[i] = agent.getOwn(i) + agent.getAllocation(i);
        }

        // the already owned hotel rooms
        int[] hotelOwns = new int[8];
        for (int i = 0; i < 8; i++) {
            hotelOwns[i] = agent.getOwn(i + 8);
        }

        // endregion Collect Data

        // get rid of clients with feasible travel package
        // region Client Type 1

        for (int i = 0; i < clients.size(); ) {

            // get the preferences of the hotels get only
            // the first day to stay in the hotel (arrival day)
            // the last day to stay in the hotel (the day before the departure day)
            client = clients.get(i);
            firstHotelDay = clientPreferences[client][TACAgent.ARRIVAL];
            lastHotelDay = clientPreferences[client][TACAgent.DEPARTURE] - 1;
            hotelType = this.getHotelType(client);
            hotelIndexOffset = hotelType == TACAgent.TYPE_CHEAP_HOTEL ? 0 : 4;

            // then check if for these preference days, a hotel room was owned
            isCompleteHotelStay = true;
            for (int j = firstHotelDay; j <= lastHotelDay; j++) {
                isCompleteHotelStay = isCompleteHotelStay && hotelOwns[hotelIndexOffset + j - 1] > 0;
            }

            if (isCompleteHotelStay) {

                // if we don't own an in-flight/out-flight, add one to the new allocations
                // else, remove from own
                if (flightOwns[firstHotelDay - 1] < 1) {
                    flightAllocations[firstHotelDay - 1]++;
                } else {
                    flightOwns[firstHotelDay - 1]--;
                }
                if (flightOwns[lastHotelDay - 1 + 4] < 1) {
                    flightAllocations[lastHotelDay - 1 + 4]++;
                } else {
                    flightOwns[lastHotelDay - 1 + 4]--;
                }

                // remove the hotel room(s) of all the stay/trip from the owns
                // because we've (found a flight to it/allocated a flight to it)
                for (int j = firstHotelDay; j <= lastHotelDay; j++) {
                    hotelOwns[hotelIndexOffset + j - 1]--;
                }

                // now this client is completed, remove it from the list
                // so we can process other clients
                // wrapping the integer (which represents client id) into object
                // because the remove function get confused between removing the integer i
                // or removing the object with index = integer i
                clients.remove((Object) client);
            } else {

                // increment to the next client
                // increment only if the current client was not removed
                i++;
            }
        }

        // endregion Client Type 1

        // for other clients with no feasible package but first/last hotel day
        // try shorten the hotel stay then add flight allocations to these shortened stays
        // region Client Type 2

        boolean isFirstHotelDay;
        boolean isLastHotelDay;
        boolean clientDeleted;
        log.fine(FlightLogPrefix + "Clients with un-feasible travel package yet: " + Arrays.toString(clients.toArray()));
        for (int i = 0; i < clients.size(); ) {

            clientDeleted = false;
            client = clients.get(i);

            firstHotelDay = clientPreferences[client][TACAgent.ARRIVAL];
            lastHotelDay = clientPreferences[client][TACAgent.DEPARTURE] - 1;
            hotelType = this.getHotelType(client);
            hotelIndexOffset = hotelType == TACAgent.TYPE_CHEAP_HOTEL ? 0 : 4;
            isFirstHotelDay = hotelOwns[hotelIndexOffset + firstHotelDay + -1] > 0;
            isLastHotelDay = hotelOwns[hotelIndexOffset + lastHotelDay - 1] > 0;

            log.fine(FlightLogPrefix + "This is client with yet not feasible travel package: " + (client + 1));
            log.fine(FlightLogPrefix + "firstHotelDay? " + isFirstHotelDay);
            log.fine(FlightLogPrefix + "lastHotelDay?  " + isLastHotelDay);

            // check if client has hotel rooms in first/last days
            isCompleteHotelStay = false;
            if (isFirstHotelDay || isLastHotelDay) {

                log.fine(FlightLogPrefix + "Client has first or last hotel day, we're trying to feasible his stay.");

                if (firstHotelDay == lastHotelDay) {
                    isCompleteHotelStay = true;
                } else {

                    // shorten the stay till you close on 1 day or more with available hotel rooms
                    while (!isCompleteHotelStay && lastHotelDay > firstHotelDay) {
                        if (isFirstHotelDay) {
                            lastHotelDay--;
                            isCompleteHotelStay = hotelOwns[hotelIndexOffset + lastHotelDay - 1] > 0;
                        } else if (isLastHotelDay) {
                            firstHotelDay++;
                            isCompleteHotelStay = hotelOwns[hotelIndexOffset + firstHotelDay + -1] > 0;
                        }
                    }
                }

                // now we have a complete stay with new first/last hotel day, then adjust:
                // preferences, hotelOwns, flightOwns, flightAllocations
                if (isCompleteHotelStay) {

                    // if we don't own an in-flight/out-flight, add one to the new allocations
                    // else, remove from own
                    if (flightOwns[firstHotelDay - 1] < 1) {
                        flightAllocations[firstHotelDay - 1]++;
                    } else {
                        flightOwns[firstHotelDay - 1]--;
                    }
                    if (flightOwns[lastHotelDay - 1 + 4] < 1) {
                        flightAllocations[lastHotelDay - 1 + 4]++;
                    } else {
                        flightOwns[lastHotelDay - 1 + 4]--;
                    }

                    // remove the hotel room(s) of all the stay/trip from the owns
                    // because we've (found a flight to it/allocated a flight to it)
                    for (int j = firstHotelDay; j <= lastHotelDay; j++) {
                        hotelOwns[hotelIndexOffset + j - 1]--;
                    }

                    // adjust preferences
                    clientPreferences[i][TACAgent.ARRIVAL] = firstHotelDay;
                    clientPreferences[i][TACAgent.DEPARTURE] = lastHotelDay + 1;

                    // now this client is completed, remove it from the list
                    // so we can process other clients
                    // wrapping the integer (which represents client id) into object
                    // because the remove function get confused between removing the integer i
                    // or removing the object with index = integer i
                    clients.remove((Object) client);
                    clientDeleted = true;

                } else {
                    log.warning(FlightLogPrefix + "This can't be happening, the client has a confirmed first/last hotel day, so how come we're not able to make a feasible travel package for him.");
                    log.warning(FlightLogPrefix + "Client: " + (client + 1));
                    log.warning(FlightLogPrefix + "First Hotel Day: " + firstHotelDay);
                    log.warning(FlightLogPrefix + "Last Hotel Day : " + isLastHotelDay);
                }
            }

            if (!clientDeleted) {
                i++;
            }
        }

        // endregion Client Type 2

        // last type is clients with neither the first not last hotel day
        // try check for any room the client have and make a feasible package out of it
        // region Client Type 3

        //boolean clientHasRoom = false;
        log.fine(FlightLogPrefix + "Clients with no first and last hotel day : " + Arrays.toString(clients.toArray()));
        for (int i = 0; i < clients.size(); ) {

            clientDeleted = false;
            client = clients.get(i);

            firstHotelDay = clientPreferences[client][TACAgent.ARRIVAL];
            lastHotelDay = clientPreferences[client][TACAgent.DEPARTURE] - 1;
            hotelType = this.getHotelType(client);
            hotelIndexOffset = hotelType == TACAgent.TYPE_CHEAP_HOTEL ? 0 : 4;

            // keep shortening the stay till you find a feasible travel package
            // i.e connected day(s) with all having hotel rooms
            isCompleteHotelStay = false;
            while (!isCompleteHotelStay && lastHotelDay > firstHotelDay) {
                lastHotelDay--;
                isCompleteHotelStay = hotelOwns[hotelIndexOffset + lastHotelDay - 1] > 0;
            }
            isCompleteHotelStay = false;
            while (!isCompleteHotelStay && lastHotelDay > firstHotelDay) {
                firstHotelDay++;
                isCompleteHotelStay = hotelOwns[hotelIndexOffset + firstHotelDay - 1] > 0;
            }

            // now we have a complete stay with new first/last hotel day, then adjust:
            // preferences, hotelOwns, flightOwns, flightAllocations
            if (isCompleteHotelStay) {

                // if we don't own an in-flight/out-flight, add one to the new allocations
                // else, remove from own
                if (flightOwns[firstHotelDay - 1] < 1) {
                    flightAllocations[firstHotelDay - 1]++;
                } else {
                    flightOwns[firstHotelDay - 1]--;
                }
                if (flightOwns[lastHotelDay - 1 + 4] < 1) {
                    flightAllocations[lastHotelDay - 1 + 4]++;
                } else {
                    flightOwns[lastHotelDay - 1 + 4]--;
                }

                // remove the hotel room(s) of all the stay/trip from the owns
                // because we've (found a flight to it/allocated a flight to it)
                for (int j = firstHotelDay; j <= lastHotelDay; j++) {
                    hotelOwns[hotelIndexOffset + j - 1]--;
                }

                // adjust preferences
                clientPreferences[i][TACAgent.ARRIVAL] = firstHotelDay;
                clientPreferences[i][TACAgent.DEPARTURE] = lastHotelDay + 1;

                // now this client is completed, remove it from the list
                // so we can process other clients
                // wrapping the integer (which represents client id) into object
                // because the remove function get confused between removing the integer i
                // or removing the object with index = integer i
                clients.remove((Object) client);
                clientDeleted = true;
            }

            if (!clientDeleted) {
                i++;
            }
        }

        // endregion Client Type 3

        // still there might be clients who fails to get at least one room
        // these clients we loose their utilities completely
        log.fine(FlightLogPrefix + "Totally Dropped Clients: " + Arrays.toString(clients.toArray()));

        // finally, add the processed new allocations
        int allocation;
        for (int i = 0; i < 8; i++) {
            allocation = agent.getAllocation(i) + flightAllocations[i];
            agent.setAllocation(i, allocation);
        }
    }

    /**
     * Read the status of the flight auctions, process it and then decide to bid
     * according to the current allocations and price history.
     */
    private void flightAuctionsProcessor() {

        long leftTime = agent.getGameTimeLeft();
        long gameTime = agent.getGameTime();
        int allocation;

        // check if it is the last 10 seconds before the game closes
        // or if the time since game start is less than 2 min (prices are good generally in the 1st 2 min)
        // then just bid with all the current allocations
        if (gameTime < 2 * 60 * 1000 || leftTime <= 10 * 1000) {

            // send bids for all flight auctions with allocations
            for (int i = 0; i < 8; i++) {

                allocation = agent.getAllocation(i);

                if (allocation > 0) {
                    flightSendBid(i);
                }
            }

        } else {

            // here means we still have at least one last update for the flight auctions
            // Bidding strategy is very simple, for a certain flight auction:
            // - get the price history
            // - estimate when is the minimum price (now or then)
            // - if now bid, if then wait
            // after 10 seconds, the method will be invoked
            // and the whole step keeps working

            FlightPricePredictor predictor;
            double[] prices;
            boolean shouldBuy;

            // loop on all the flight auctions
            // check if the auction has allocations
            for (int i = 0; i < 8; i++) {

                allocation = agent.getAllocation(i);

                if (allocation > 0) {

                    // check if to bid now or later
                    prices = listToArray(flightPrices.get(i));
                    predictor = new FlightPricePredictor(prices);
                    shouldBuy = predictor.shouldBuy();
                    if (shouldBuy) {
                        flightSendBid(i);
                    }
                }
            }
        }
    }

    /**
     * Send bid to all of the given flight auction according to the current allocations.
     * Don't bother yourself with the own flights, the flightAllocationsProcessor()
     * takes good care of that.
     */
    private void flightSendBid(int auction) {

        Quote quote;
        int allocation = agent.getAllocation(auction);

        if (allocation > 0) {

            // create new bid and submit it
            quote = agent.getQuote(auction);
            buyBidPrices[auction] = quote.getAskPrice();
            Bid bid = new Bid(auction);
            bid.addBidPoint(allocation, buyBidPrices[auction]);
            agent.submitBid(bid);

            // reset the allocation
            allocation = 0;
            agent.setAllocation(auction, allocation);
        }
    }

    /**
     * Collects the prices of the flight auctions (8 auctions).
     */
    private void flightPricesCollector() {

        // get prices of flights for all 8 flight auctions
        Quote quote;
        for (int i = 0; i < 8; i++) {
            quote = agent.getQuote(i);
            float value = quote.getAskPrice();
            long time = agent.getGameTime();
            flightPrices.get(i).add(new PricePoint(value, time));
        }
    }

    /**
     * Print all the flight prices.
     */
    private void flightPrintPrices() {

        ArrayList<PricePoint> _prices;
        PricePoint _price;

        log.fine(LogPrefix + "Start Printing All Flight Prices");
        for (int i = 0; i < flightPrices.size(); i++) {
            log.fine(LogPrefix + "Printing (time, price) for " + agent.getAuctionTypeAsString(i) + ":");
            _prices = flightPrices.get(i);
            for (int j = 0; j < _prices.size(); j++) {
                _price = _prices.get(j);
                log.fine(LogPrefix + _price.seconds + ", " + _price.value);
            }
        }
        log.fine(LogPrefix + "Finish Printing All Flight Prices");
    }

    /**
     * Print the success/error rate in predicting the time when the flight prices are the minimum
     * and buying at this time. Also, print the amount of money gained/lost due to these predictions.
     */
    private void flightPredictionResults() {

        Quote quote;
        float[] profits = new float[8];
        float[] actualProfitsByInitialPrices = new float[8];
        float[] actualProfitsByFinalPrices = new float[8];

        float profitsSum = 0;
        float actualProfitsSumByInitialPrices = 0;
        float actualProfitsSumByFinalPrices = 0;
        int successRate = 0;
        int actualSuccessRate = 0;
        int own;
        int nonZeroAuctionCounter = 0;
        float[] initialPrices = new float[8];
        float[] buyingPrices = new float[8];
        float[] finalPrices = new float[8];

        for (int i = 0; i < 8; i++) {

            quote = agent.getQuote(i);
            own = agent.getOwn(i);

            initialPrices[i] = flightPrices.get(i).get(0).value;
            buyingPrices[i] = buyBidPrices[i];
            finalPrices[i] = quote.getAskPrice();
            profits[i] = initialPrices[i] - buyingPrices[i];
            actualProfitsByInitialPrices[i] = (initialPrices[i] - buyingPrices[i]) * own;
            actualProfitsByFinalPrices[i] = (finalPrices[i] - buyingPrices[i]) * own;

            profitsSum += profits[i];
            actualProfitsSumByInitialPrices += actualProfitsByInitialPrices[i];
            actualProfitsSumByFinalPrices += actualProfitsByFinalPrices[i];

            if (profits[i] >= 0) {
                successRate++;
            }

            if (own > 0) {
                nonZeroAuctionCounter++;
                if (profits[i] >= 0) {
                    actualSuccessRate++;
                }
            }
        }

        double successRatio = 100 * successRate / (double) 8;
        double actualSuccessRatio = 100 * actualSuccessRate / (double) nonZeroAuctionCounter;
        log.fine(LogPrefix + "Flight Results");
        log.fine(LogPrefix + "                    Initial Prices: " + Arrays.toString(initialPrices));
        log.fine(LogPrefix + "                     Buying Prices: " + Arrays.toString(buyingPrices));
        log.fine(LogPrefix + "                      Final Prices: " + Arrays.toString(finalPrices));
        log.fine(LogPrefix + "               Predictions profits: " + Arrays.toString(profits));
        log.fine(LogPrefix + "         Actual profits By Initial: " + Arrays.toString(actualProfitsByInitialPrices));
        log.fine(LogPrefix + "           Actual profits By Final: " + Arrays.toString(actualProfitsByFinalPrices));
        log.fine(LogPrefix + "                     Success ratio: " + successRatio + " %");
        log.fine(LogPrefix + "              Actual success ratio: " + actualSuccessRatio + " %");
        log.fine(LogPrefix + "            Profits (1 flight/day): " + profitsSum);
        log.fine(LogPrefix + "            Profits Sum by Initial: " + actualProfitsSumByInitialPrices);
        log.fine(LogPrefix + "              Profits Sum by Final: " + actualProfitsSumByFinalPrices);
    }

    // endregion Private Methods [Flight]

    // region Private Methods [Hotel]

    /**
     * Set the allocations for the hotel auctions according to the client preferences.
     */
    private void hotelSetAllocations() {

        int auction;
        int allocation;
        int inFlightDay;
        int outFlightDay;
        int hotelType;

        // loop on the 8 clients and get their preferences
        // note: we have 8 clients for each game
        for (int i = 0; i < 8; i++) {

            // get flight preferences (arrival and departure days)
            inFlightDay = clientPreferences[i][TACAgent.ARRIVAL];
            outFlightDay = clientPreferences[i][TACAgent.DEPARTURE];
            // get the preference for the hotel type (according value)
            // if the hotel value is greater than 70 we will select the expensive hotel (type = 1)
            hotelType = getHotelType(i);
            // allocate a hotel night for each day that the agent stays
            for (int j = inFlightDay; j < outFlightDay; j++) {
                auction = TACAgent.getAuctionFor(TACAgent.CAT_HOTEL, hotelType, j);
                allocation = agent.getAllocation(auction) + 1;
                agent.setAllocation(auction, allocation);
            }
        }

        for (int i = 0; i < 8; i++) {
            hotelInitialAllocations[i] = agent.getAllocation(i + 8);
        }
    }

    /**
     * @param quote
     */
    private void hotelQuoteUpdated(Quote quote) {

        int auction = quote.getAuction();

        // only process the clear auctions
        // the recently closed one will be processed later
        // because there is a delay of the correct information
        // of the Own goods of the recently-closed auction
        int auctionStatus = quote.getAuctionStatus();
        if (isAuctionClear(auctionStatus)) {
            hotelAuctionProcessor(auction);
        }
    }

    /**
     * Update the allocation of the closed auction (to reflect the own goods) and process it.
     *
     * @param auction
     */
    private void hotelAuctionClosed(int auction) {

        log.fine(HotelLogPrefix + "Hotel auction " + agent.getAuctionTypeAsString(auction) + " closed!");

        // raise down the flag of last-second strategy
        hotelBiddingMode = HotelAuctionBiddingMode.Normal;

        // add it to the list of old allocations and update the allocation
        closedHotelAuctions.add(auction);
        int oldAllocation = agent.getAllocation(auction);
        int own = agent.getOwn(auction);
        int newAllocation = oldAllocation - own;
        agent.setAllocation(auction, newAllocation);

        // collect the auction data and add it to history
        // only collect auctions we've participated in
        if (oldAllocation > 0) {
            Quote quote = agent.getQuote(auction);
            HotelAuctionHistory history = new HotelAuctionHistory(auction, oldAllocation, own, buyBidPrices[auction], quote.getAskPrice());
            hotelAuctionsHistory.add(history);
        }

        // don't forget to process the auction
        hotelAuctionProcessor(auction);

        log.fine(HotelLogPrefix + "Old allocation: " + oldAllocation);
        log.fine(HotelLogPrefix + "New allocation: " + newAllocation);
        log.fine(HotelLogPrefix + "Own: " + own);
    }

    /**
     * Call the last-second bidding strategy at the end of the 1-min period of a hotel auction.
     */
    private void hotelAuctionsTimerTick() {

        final int second = 1000;
        final int minute = second * 60;
        long gameLeftMilli = agent.getGameTimeLeft();
        long gameLeftSec = gameLeftMilli / second;

        // stop timer after 8 minutes the last auction is closed
        if (gameLeftSec < 58) {
            hotelAuctionsTimer.stop();
            return;
        }

        double auctionLeftSec = (gameLeftMilli % minute) / second;
        if (auctionLeftSec <= 2 && hotelBiddingMode == HotelAuctionBiddingMode.Normal) {

            log.fine(HotelLogPrefix + "Left seconds till    game close (sec): " + gameLeftSec);
            log.fine(HotelLogPrefix + "                  auction close (sec): " + auctionLeftSec);
            log.fine(HotelLogPrefix + "Fire you final weapons, your final auction bid !");

            // raise up the flag of last-second strategy
            hotelBiddingMode = HotelAuctionBiddingMode.Final;

            // process only the clear (opened/unclosed) auctions
            int auction;
            for (int i = 0; i < 8; i++) {
                auction = i + 8;
                if (!closedHotelAuctions.contains(auction)) {
                    hotelAuctionProcessor(auction);
                }
            }
        }
    }

    /**
     * Start processing the hotel auctions and bidding for them. This is called
     * only the first time when the game starts.
     */
    private void hotelAuctionsProcessor() {

        // we need to sleep until the status of the auction is Clear
        // we need a thread so as not to stop the main one
        // (if main one is stopped, the status will not change)
        Thread thread = new Thread() {
            public void run() {

                Quote quote;
                long sleepDuration = 100;
                int auctionStatus = Quote.AUCTION_INITIALIZING;

                // hotel auctionIDs are from 8-15
                for (int auction = 8; auction < 16; auction++) {
                    quote = agent.getQuote(auction);
                    while (auctionStatus == Quote.AUCTION_INITIALIZING) {
                        auctionStatus = quote.getAuctionStatus();
                        if (isAuctionClear(auctionStatus)) {
                            hotelAuctionProcessor(auction);
                        } else {
                            try {
                                Thread.sleep(sleepDuration);
                            } catch (Exception exp) {
                                log.fine(HotelLogPrefix + "Error in Thread.sleep in hotelAuctionsProcessor()");
                            }
                        }
                    }
                }
            }
        };
        thread.start();
    }

    /**
     * Read the status of the current hotel auction, process it and decide to add/update a bid.
     */
    private void hotelAuctionProcessor(int auction) {

        // TO DO
        // somehow, HQW, hasHQW of the quote need to be considered

        Quote quote;
        Bid oldBid;
        Bid newBid;
        int allocation;
        int auctionStatus;
        int oldBidStatus;

        quote = agent.getQuote(auction);
        auctionStatus = quote.getAuctionStatus();

        // check if we still need more allocations
        allocation = agent.getAllocation(auction);
        if (allocation < 1) {
            return;
        }

        // check if auction is closed
        // that means we're fucked up here, because we still have allocations
        // need to be fulfilled, try reallocation
        if (!isAuctionClear(auctionStatus)) {
            log.fine(HotelLogPrefix + "Here an auction needs reallocation: " + agent.getAuctionTypeAsString(auction));
            log.fine(HotelLogPrefix + "                     status string: " + quote.getAuctionStatusAsString() + ", status number: " + auctionStatus);
            log.fine(HotelLogPrefix + "                              owns: " + agent.getOwn(auction));
            log.fine(HotelLogPrefix + "                       allocations: " + allocation);
            log.fine(HotelLogPrefix + "                          my price: " + buyBidPrices[auction]);
            log.fine(HotelLogPrefix + "                         ask price: " + quote.getAskPrice());
            hotelAuctionReallocate(auction, allocation);
            return;
        }

        // if current price is so high don't put higher margin
        boolean quitBidHighPrice = (hotelBiddingMode == HotelAuctionBiddingMode.Final && buyBidPrices[auction] > 550)
                || (hotelBiddingMode == HotelAuctionBiddingMode.Normal && buyBidPrices[auction] > 400);
        if (quitBidHighPrice) {
            log.fine(HotelLogPrefix + "Price is too high, we quit bidding for auction: " + TACAgent.getAuctionTypeAsString(auction));
            log.fine(HotelLogPrefix + "Ask price: " + quote.getAskPrice());
            log.fine(HotelLogPrefix + "Our price: " + buyBidPrices[auction]);
            return;
        }

        // get the old bid, if null submit the new
        oldBid = agent.getBid(auction);
        if (oldBid == null) {
            newBid = hotelNewBid(quote, allocation, auction);
            agent.submitBid(newBid);
        } else {
            // if rejected because of (15: price not beat), then update the bid
            // if still valid, update the quote with the current ask price + margin
            oldBidStatus = oldBid.getProcessingState();
            if (oldBidStatus == Bid.REJECTED && oldBid.getRejectReason() == 15) {
                newBid = hotelNewBid(quote, allocation, auction);
                agent.replaceBid(oldBid, newBid);
            } else if (oldBidStatus == Bid.VALID) {
                newBid = hotelUpdatedBid(quote, allocation, auction);
                agent.replaceBid(oldBid, newBid);
            }
        }
    }

    /**
     * The auction with the given id closed, try the reallocation to still have a feasible travel package
     *
     * @param auction
     */
    private void hotelAuctionReallocate(int auction, int allocation) {

        int hotelFirstDay;
        int hotelLastDay;
        int hotelType;
        int auctionDay;
        boolean succeeded = false;

        // loop on all the clients
        for (int i = 0; i < 8 && allocation > 0; i++) {

            hotelType = getHotelType(i);

            // check if this client has asked for allocation in the current auction type (i.e current hotel type)
            if ((hotelType == TACAgent.TYPE_CHEAP_HOTEL && auction < 12)
                    || (hotelType == TACAgent.TYPE_GOOD_HOTEL && auction > 11)) {

                auctionDay = hotelType == TACAgent.TYPE_CHEAP_HOTEL ? auction - 7 : auction - 11;
                hotelFirstDay = clientPreferences[i][TACAgent.ARRIVAL];
                hotelLastDay = clientPreferences[i][TACAgent.DEPARTURE] - 1;

                // check if the current auction day is the client's first/last hotel days
                if ((auctionDay == hotelFirstDay || auctionDay == hotelLastDay)
                        && (hotelLastDay > hotelFirstDay)) {

                    int days = 1;

                    // try shorten the stay by n days
                    while (days < 4 && !succeeded) {
                        log.fine(HotelLogPrefix + "Try shorten stay by " + days + " days for action: " + agent.getAuctionTypeAsString(auction) + " for client: " + (i + 1));
                        succeeded = hotelShortenStay(i, auction, days, hotelFirstDay, hotelLastDay, auctionDay);
                        days++;
                        log.fine(HotelLogPrefix + "Shorten Succeeded? " + succeeded);
                        log.fine(HotelLogPrefix + "First Hotel Day: " + hotelFirstDay);
                        log.fine(HotelLogPrefix + "Last  Hotel Day: " + hotelLastDay);
                    }
                    if (!succeeded) {
                        // if couldn't shorten the stay then remove this allocation
                        // so the hotelAuctionProcess() don't call hotelAuctionReallocate()
                        // again for this allocation
                        // we might add more sophisticated strategy here
                        // like changing the whole hotel for the client
                        // Note that: we can't expand the stay as the current day (current auction)
                        // is already lost, i.e a day in the middle of the stay is already lost
                        allocation--;
                        agent.setAllocation(auction, allocation);
                        log.fine(HotelLogPrefix + "Failed to shorten stay for action: " + agent.getAuctionTypeAsString(auction) + " for client: " + i);
                    }
                }
            }
        }
    }

    /**
     * Try to shorten the stay of the given client in the given auction.
     * Shorten the stay by the amount of the given days (final stay = original stay - days).
     *
     * @param auction
     * @return
     */
    private boolean hotelShortenStay(int client, int auction, int shortenByDays, int hotelFirstDay, int hotelLastDay, int auctionDay) {

        boolean succeeded = false;
        int flightType;
        int otherDayAuction;
        int otherDayAuctionStatus;

        flightType = (auctionDay == hotelFirstDay) ? TACAgent.TYPE_INFLIGHT : TACAgent.TYPE_OUTFLIGHT;

        log.fine(HotelLogPrefix + "hotelShortenStay() is called");
        log.fine(HotelLogPrefix + "flightType " + ((flightType == TACAgent.TYPE_INFLIGHT) ? "InFlight" : "OutFlight"));
        log.fine(HotelLogPrefix + "auctionDay " + auctionDay);

        // now check if the trip days can be shorten
        if ((hotelLastDay - hotelFirstDay) >= shortenByDays) {

            // shorten by 2 ways, either shift the arrival day forward
            // or shift the departure day backward
            // in both ways, you should check if the auction of the new day is opened
            otherDayAuction = -1;
            if (flightType == TACAgent.TYPE_INFLIGHT) {
                // 1. try shift arrival day forward
                otherDayAuctionStatus = agent.getQuote(auction + shortenByDays).getAuctionStatus();
                if (isAuctionClear(otherDayAuctionStatus)) {
                    otherDayAuction = auction + shortenByDays;
                }
            } else {
                // 2. try shift departure day backward
                otherDayAuctionStatus = agent.getQuote(auction - shortenByDays).getAuctionStatus();
                if (isAuctionClear(otherDayAuctionStatus)) {
                    otherDayAuction = auction - shortenByDays;
                }
            }

            // if alternative day is found (either forward or backward)
            // save this in the client preferences
            // do the reallocation (by de-allocating the abandoned days)
            if (otherDayAuction != -1) {

                // remove one allocation point (if exist) from the current auction
                // and every other auction between the current auction and the the otherAuction
                int allocation;
                if (flightType == TACAgent.TYPE_INFLIGHT) {
                    for (int i = auction; i < otherDayAuction; i++) {
                        allocation = agent.getAllocation(i);
                        if (allocation > 0) {
                            allocation--;
                            agent.setAllocation(i, allocation);
                        } else {
                            log.warning(HotelLogPrefix + "This is un-realistic 1");
                        }
                    }
                } else {
                    for (int i = auction; i > otherDayAuction; i--) {
                        allocation = agent.getAllocation(i);
                        if (allocation > 0) {
                            allocation--;
                            agent.setAllocation(i, allocation);
                        } else {
                            log.warning(HotelLogPrefix + "This is un-realistic 2");
                        }
                    }
                }

                // save client preference
                // either the arrival day is forward or the departure date is backward
                if (flightType == TACAgent.TYPE_INFLIGHT) {
                    clientPreferences[client][TACAgent.ARRIVAL] += shortenByDays;
                } else {
                    clientPreferences[client][TACAgent.DEPARTURE] -= shortenByDays;
                }

                log.fine(HotelLogPrefix + "After shorten stay, flight arrival/departure days: " + clientPreferences[client][TACAgent.ARRIVAL] + ", " + clientPreferences[client][TACAgent.DEPARTURE]);

                succeeded = true;

                // only if the other-day auction is closed
                // explicitly process the it to reflect these changes
                // that's because if it iss closed, no way it will be processed again in the future
                otherDayAuctionStatus = agent.getQuote(otherDayAuction).getAuctionStatus();
                if (!isAuctionClear(otherDayAuctionStatus)) {
                    log.fine(HotelLogPrefix + "Other Auction is called for process, because it is closed, status ID: " + otherDayAuctionStatus);
                    log.fine(HotelLogPrefix + "                       Other Auction: " + agent.getAuctionTypeAsString(otherDayAuction));
                    log.fine(HotelLogPrefix + "                             Auction: " + agent.getAuctionTypeAsString(auction));
                    hotelAuctionProcessor(otherDayAuction);
                }
            }
        }

        return succeeded;
    }

    /**
     * Another strategy beside hotel shorten stay is changing the hotel preferences
     * to the counterpart hotel (if available).
     */
    @Deprecated
    private void hotelAuctionCounterParting(int auction, int allocation) {

        // Not working yet!.
        // check if the auction of the counterpart hotel is still open
        // and re-allocate the allocations of the current auction to it

        int counterpartAuction = auction < 12 ? auction + 4 : auction - 4;
        Quote counterpartQuote = agent.getQuote(counterpartAuction);
        int counterpartActionStatus = counterpartQuote.getAuctionStatus();

        // if the counterpart auction is closed as well, there is nothing we can do with this allocation
        if (!isAuctionClear(counterpartActionStatus)) {
            return;
        }

        // re-allocate
        agent.setAllocation(auction, 0);
        agent.setAllocation(counterpartAuction, allocation);

        // explicitly process this counterpart auction
        hotelAuctionProcessor(counterpartAuction);
    }

    /**
     * Create new hotel bid with increased price (hopefully to beat the quote).
     *
     * @param quote
     * @param allocation
     * @param auction
     * @return
     */
    private Bid hotelNewBid(Quote quote, int allocation, int auction) {

        int margin = hotelBidMargin(auction);

        // increase the price than the old one
        float newBidPrice = quote.getAskPrice() + margin;
        if (newBidPrice <= buyBidPrices[auction]) {
            newBidPrice = buyBidPrices[auction] + margin;
        }

        Bid newBid;
        newBid = new Bid(auction);
        newBid.addBidPoint(allocation, newBidPrice);

        // save the price for later use
        buyBidPrices[auction] = newBidPrice;

        return newBid;
    }

    /**
     * Create an updated hotel bid with the the ask price + margin (hopefully to beat the quote).
     *
     * @param quote
     * @param allocation
     * @param auction
     * @return
     */
    private Bid hotelUpdatedBid(Quote quote, int allocation, int auction) {

        int margin = hotelBidMargin(auction);

        // increase the price than the old one
        buyBidPrices[auction] = quote.getAskPrice() + margin;

        Bid updatedBid = new Bid(auction);
        updatedBid.addBidPoint(allocation, buyBidPrices[auction]);
        return updatedBid;
    }

    /**
     * The margin to be added in a new hotel bid. This margin is calculated upon how easy/hard the competition
     * was in this specific auction. How slow/rapid the prices were fluctuating and how high/low this fluctuation.
     *
     * @return
     */
    private int hotelBidMargin(int auction) {

        int margin = 1;
        ArrayList<PricePoint> prices = hotelPrices.get(auction - 8);
        int length = prices.size();
        int offset = 0;

        // offset is added to margin only in the final bidding mode
        // the fixed number 100 need to be modified
        // in the optimal case, we should estimate this number from previous games played
        if (hotelBiddingMode == HotelAuctionBiddingMode.Final) {
            int day = auction < 12 ? auction - 8 : auction - 12;
            offset = 4 * ((int) hotelAuctionDemand[day]);
            offset = offset < 100 ? 100 : offset;
        }

        // make sure we've at least 3 prices so we can predict
        if (length < 3) {

            if (length == 0) {
                margin = 1;
            } else {
                margin = (int) (buyBidPrices[auction] * 0.1);
                margin = margin < 2 ? 2 : margin;
            }

            margin += offset;
            return margin;
        }

        long[] x = new long[length];
        double[] y = new double[length];
        int predictedPrice;
        int bidPrice;
        PricePoint price;

        for (int i = 0; i < length; i++) {
            price = prices.get(i);
            x[i] = price.seconds;
            y[i] = price.value;
        }

        long agentTime = agent.getGameTime();
        long closeTime = ((agentTime / (1000 * 60)) + (agentTime % (1000 * 60) == 0 ? 0 : 1)) * 1000 * 60;
        HotelPricePredictor predictor = new HotelPricePredictor(x, y);
        predictedPrice = predictor.predict(closeTime);

        // make sure margin will increase the bid price
        bidPrice = (int) buyBidPrices[auction];
        if (bidPrice > predictedPrice) {
            predictedPrice = bidPrice + 1;
        }
        margin = predictedPrice - bidPrice;
        margin += offset;

        log.fine(HotelLogPrefix + "This is hotel price predictor for hotel auction:" + auction);
        log.fine(HotelLogPrefix + "Current time in minutes is: " + agentTime / (1000 * 60));
        log.fine(HotelLogPrefix + "Current time in milli is: " + agentTime);
        log.fine(HotelLogPrefix + "Predicted Price is: " + predictedPrice);
        log.fine(HotelLogPrefix + "Current Price is: " + buyBidPrices[auction]);
        log.fine(HotelLogPrefix + "Bid mode: " + (hotelBiddingMode == HotelAuctionBiddingMode.Final));
        log.fine(HotelLogPrefix + "Offset: " + offset);
        log.fine(HotelLogPrefix + "Margin: " + margin);
        log.fine(HotelLogPrefix + "Predicted for time (min  ): " + closeTime / (1000 * 60));
        log.fine(HotelLogPrefix + "Predicted for time (milli): " + closeTime);

        return margin;
    }

    /**
     * Collects the prices of the hotel auctions (8 auctions).
     */
    private void hotelPricesCollector(Quote quote) {

        float value = quote.getAskPrice();
        long time = agent.getGameTime();

        int auction = quote.getAuction() - 8;
        hotelPrices.get(auction).add(new PricePoint(value, time));
    }

    /**
     * Print all the hotel prices.
     */
    private void hotelPrintPrices() {

        ArrayList<PricePoint> _prices;
        PricePoint _price;

        log.fine(LogPrefix + "Start Printing All Hotel Prices");
        for (int i = 0; i < hotelPrices.size(); i++) {
            log.fine(LogPrefix + "Printing (time, price) for " + agent.getAuctionTypeAsString(i + 8) + ":");
            _prices = hotelPrices.get(i);
            for (int j = 0; j < _prices.size(); j++) {
                _price = _prices.get(j);
                log.fine(LogPrefix + _price.seconds + ", " + _price.value);
            }
        }
        log.fine(LogPrefix + "Finish Printing All Hotel Prices");
    }

    /**
     * Print the success/error rate between the predicted max price of hotel auction and the ask price
     * this auction closed with (final ask price with this auction).
     */
    private void hotelPredictionResults() {

        int length = hotelAuctionsHistory.size();
        float[] _bid_price = new float[length];
        float[] _ask_price = new float[length];
        int successRate = 0;
        HotelAuctionHistory history;

        for (int i = 0; i < length; i++) {

            history = hotelAuctionsHistory.get(i);
            _bid_price[i] = history.bidPrice;
            _ask_price[i] = history.askPrice;

            if (_bid_price[i] >= _ask_price[i]) {
                successRate++;
            }
        }

        int[] hotelFinalOwns = new int[8];
        int[] hotelFinalAllocations = new int[8];
        for (int i = 0; i < 8; i++) {
            hotelFinalOwns[i] = agent.getOwn(i + 8);
            hotelFinalAllocations[i] = hotelInitialAllocations[i] - hotelFinalOwns[i];
        }

        double successRatio = 100 * (successRate / (double) length);
        log.fine(LogPrefix + "Hotel Results: ");
        log.fine(LogPrefix + "                        bid prices: " + Arrays.toString(_bid_price));
        log.fine(LogPrefix + "                        ask prices: " + Arrays.toString(_ask_price));
        log.fine(LogPrefix + "                     success ratio: " + successRatio + " %");
        log.fine(LogPrefix + "               initial allocations: " + Arrays.toString(hotelInitialAllocations));
        log.fine(LogPrefix + "                        final owns: " + Arrays.toString(hotelFinalOwns));
        log.fine(LogPrefix + "                  final allocation: " + Arrays.toString(hotelFinalAllocations));
    }

    // endregion Private Methods [Hotel]

    // region Private Methods [Entertainment]

    /**
     * This is to calculate the whole allocations depending on the initial preferences
     * then see what goods we don't want and remove it/them from the allocations.
     */
    private void entertainmentDeAllocator() {

        // get all the owned entertainment rooms
        int[] entertainmentOwns = new int[12];
        for (int i = 0; i < 12; i++) {
            entertainmentOwns[i] = agent.getOwn(i + 16);
        }

        int firstHotelDay;
        int lastHotelDay;
        int stayDuration;
        int preferredType;
        int entertainmentAuction;
        ArrayList<Integer> preferredTypes;
        ArrayList<Integer> clientAuctions;
        ArrayList<Integer> satisfiedDays;

        // loop on all clients
        for (int i = 0; i < 8; i++) {

            // get the first-day/last-day to stay in the hotel
            // also, get list of client preferences for the entertainment
            firstHotelDay = clientPreferences[i][TACAgent.ARRIVAL];
            lastHotelDay = clientPreferences[i][TACAgent.DEPARTURE] - 1;
            clientAuctions = clientPreferredEntertainmentAuctions.get(i);
            stayDuration = (lastHotelDay - firstHotelDay) + 1;
            preferredTypes = entertainmentClientPreferredTypes(i, stayDuration);
            satisfiedDays = new ArrayList<Integer>();

            // loop on all client's days
            for (int j = firstHotelDay; j <= lastHotelDay && preferredTypes.size() > 0; j++) {

                // for each day, the client can be satisfied by any preferred auction in this day
                for (int k = 0; k < preferredTypes.size(); ) {
                    preferredType = preferredTypes.get(k);
                    entertainmentAuction = TACAgent.getAuctionFor(TACAgent.CAT_ENTERTAINMENT, preferredType, j);
                    if (entertainmentOwns[entertainmentAuction - 16] > 0) {
                        entertainmentOwns[entertainmentAuction - 16]--;
                        clientAuctions.add(entertainmentAuction);
                        satisfiedDays.add(j);
                        preferredTypes.remove((Object) preferredType);
                        break;
                    } else {
                        k++;
                    }
                }
            }

            // now loop again on the client's un-satisfied days and fill them
            for (int j = firstHotelDay; j <= lastHotelDay && preferredTypes.size() > 0; j++) {
                if (!satisfiedDays.contains(j)) {
                    entertainmentAuction = TACAgent.getAuctionFor(TACAgent.CAT_ENTERTAINMENT, preferredTypes.get(0), j);
                    clientAuctions.add(entertainmentAuction);
                    satisfiedDays.add(j);
                    preferredTypes.remove(0);
                }
            }
        }

        // now, for all the rest of the own entertainment
        // remove it from the agent.Own and put it in the deAllocation list
        int updatedOwn;
        log.fine(EntertainmentLogPrefix + "Entertainment Auctions Old Own Before De-Allocations");
        for (int i = 0; i < 12; i++) {
            entertainmentAuction = i + 16;
            entertainmentDeAllocations[i] = entertainmentOwns[i];
            updatedOwn = agent.getOwn(entertainmentAuction);
            log.fine(EntertainmentLogPrefix + "Auction: " + TACAgent.getAuctionTypeAsString(entertainmentAuction) + " own: " + updatedOwn);
            updatedOwn -= entertainmentDeAllocations[i];
            agent.setOwn(entertainmentAuction, updatedOwn);
        }

        log.fine(EntertainmentLogPrefix + "De-Allocating Un-needed entertainment owns");
        log.fine(EntertainmentLogPrefix + "Un-wanted: " + Arrays.toString(entertainmentDeAllocations));
        log.fine(EntertainmentLogPrefix + "Clients' Preferences calculated!");
        for (int i = 0; i < clientPreferredEntertainmentAuctions.size(); i++) {
            ArrayList<Integer> list = clientPreferredEntertainmentAuctions.get(i);
            log.fine(EntertainmentLogPrefix + "Client " + (i + 1) + " has ent. auctions: ");
            for (int j = 0; j < list.size(); j++) {
                log.fine(EntertainmentLogPrefix + "                            " + TACAgent.getAuctionTypeAsString(list.get(j)));
            }
        }
    }

    /**
     * Process the updated entertainment quote and see if a bid is need to be updated or not.
     *
     * @param quote
     */
    private void entertainmentQuoteUpdated(Quote quote) {

        // nothing need to be done here because the entertainment
        // timer is taking good care of this
    }

    /**
     * Process the update bid.
     *
     * @param bid
     */
    private void entertainmentBidUpdated(Bid bid) {

        int bidStatus = bid.getProcessingState();
        String bidString = bid.getBidString();
        int quantity = bid.getQuantity();
        int auction = bid.getAuction();
        int boughtQuantity;
        int soldQuantity;

        // according to quantity quantity, check if it is sell/buy bid
        if (quantity > 0) {

            log.fine(EntertainmentLogPrefix + "Buy Bid Updated, id: " + bid.getID());
            log.fine(EntertainmentLogPrefix + "             Status: " + bid.getProcessingStateAsString());
            log.fine(EntertainmentLogPrefix + "         allocation: " + bid.getQuantity());
            log.fine(EntertainmentLogPrefix + "         bid string: " + bid.getBidString());
            log.fine(EntertainmentLogPrefix + "           bid hash: " + bid.getBidHash());

            // see when the bid is cleared or event part of it was cleared
            if (bidStatus == Bid.VALID) {
                if (bidString.length() == 0) {
                    boughtQuantity = quantity;
                } else {
                    boughtQuantity = quantity - quantityFromBidString(bidString);
                }

                // add bought quantity to own and remove it from bid allocations
                entertainmentBidsDeAllocations[auction - 16] -= boughtQuantity;
                agent.setOwn(auction, agent.getOwn(auction) + boughtQuantity);
            }

        } else if (quantity < 0) {

            log.fine(EntertainmentLogPrefix + "Sell Bid Updated, id: " + bid.getID());
            log.fine(EntertainmentLogPrefix + "              Status: " + bid.getProcessingStateAsString());
            log.fine(EntertainmentLogPrefix + "          allocation: " + bid.getQuantity());
            log.fine(EntertainmentLogPrefix + "          bid string: " + bid.getBidString());
            log.fine(EntertainmentLogPrefix + "          bid hash: " + bid.getBidHash());

            // see when the bid is cleared or event part of it was cleared
            if (bidStatus == Bid.VALID) {
                if (bidString.length() == 0) {
                    soldQuantity = (-quantity);
                } else {
                    soldQuantity = (-quantity) - (-quantityFromBidString(bidString));
                }

                // remove sold quantity from bid de-allocations
                entertainmentBidsDeAllocations[auction - 16] -= soldQuantity;
            }
        }
    }

    /**
     * Call the entertainment auction processor.
     */
    private void entertainmentAuctionsTimerTick() {

        // process the allocation
        entertainmentAllocationsProcessor();

        // process the de-allocations
        entertainmentDeAllocationsProcessor();

        // process allocations and auctions for all entertainment auctions
        // auctions: 16-27
        int auction;
        Quote quote;
        for (int i = 0; i < 12; i++) {
            auction = i + 16;
            quote = agent.getQuote(auction);
            entertainmentAuctionProcessor(auction, quote);
        }
    }

    /**
     * Process the updated quotes of the entertainment auctions and see if a bid is need to be updated/added or not.
     */
    private void entertainmentAllocationsProcessor() {

        // to get what the clients demands for entertainment tickets
        // loop on them, find their arrival/departure dates
        // and find what type of entertainment they've asked for
        // we're only interested in clients who have hotel rooms in these days:
        // - all the hotel days for the stay
        // - departure day and any adjacent previous days (if exist)
        // - arrival day and any adjacent next days

        // the additional allocations for ticket we want to buy for each entertainment auction
        // additional allocation after assessing the current situation of bought hotels
        int[] entertainmentAllocations = new int[12];
        int auction;

        // the already owned hotel rooms
        int[] hotelOwns = new int[8];
        for (int i = 0; i < 8; i++) {
            auction = i + 8;
            hotelOwns[i] = agent.getOwn(auction);
        }

        // get all the owned entertainment rooms
        // practically, own hear means agent.getOwn() + agent.getAllocation() + entertainmentBidsAllocations
        // because we might have added flight allocations in a previous flightAuctionProcessing
        int[] entertainmentOwns = new int[12];
        for (int i = 0; i < 12; i++) {
            auction = i + 16;
            entertainmentOwns[i] = agent.getOwn(auction) + agent.getAllocation(auction) + entertainmentBidsAllocations[i];
        }

        int firstHotelDay;
        int lastHotelDay;
        int hotelType;
        int hotelIndexOffset;
        int entertainmentAuction;
        boolean isFirstDay;
        boolean isLastDay;
        boolean isCompleteStay;
        ArrayList<Integer> clientAuctions;
        ArrayList<Integer> days;

        // loop on all clients
        for (int i = 0; i < 8; i++) {

            // get the preferences of the hotels get only
            // the first day to stay in the hotel (arrival day)
            // the last day to stay in the hotel (the day before the departure day)
            // then check if for these preference days, a hotel room was owned
            firstHotelDay = clientPreferences[i][TACAgent.ARRIVAL];
            lastHotelDay = clientPreferences[i][TACAgent.DEPARTURE] - 1;
            hotelType = getHotelType(i);
            hotelIndexOffset = hotelType == TACAgent.TYPE_CHEAP_HOTEL ? 0 : 4;
            isFirstDay = hotelOwns[hotelIndexOffset + firstHotelDay - 1] > 0;
            isLastDay = hotelOwns[hotelIndexOffset + lastHotelDay - 1] > 0;
            clientAuctions = clientPreferredEntertainmentAuctions.get(i);
            days = entertainmentAuctionsToHotelDays(clientAuctions);

            if (isFirstDay || isLastDay) {

                // check if all the hotel night(s) for the whole stay/trip is(are) reserved
                isCompleteStay = true;
                for (int j = firstHotelDay; j <= lastHotelDay; j++) {
                    isCompleteStay = isCompleteStay && hotelOwns[hotelIndexOffset + j - 1] > 0;
                }

                // now if complete hotel stay is found
                if (isCompleteStay) {

                    log.fine(EntertainmentLogPrefix + "This is a complete hotel stay that need entertainment fulfillment");
                    log.fine(EntertainmentLogPrefix + "Days: " + Arrays.toString(days.toArray()));
                    log.fine(EntertainmentLogPrefix + "Client Auctions: " + Arrays.toString(clientAuctions.toArray()));

                    for (int j = firstHotelDay; j <= lastHotelDay; j++) {
                        entertainmentAuction = clientAuctions.get(days.indexOf(j));
                        entertainmentAuction -= 16;
                        // either subtract from own entertainment
                        // or add new allocation
                        if (entertainmentOwns[entertainmentAuction] < 1) {
                            entertainmentAllocations[entertainmentAuction]++;
                        } else {
                            entertainmentOwns[entertainmentAuction]--;
                        }
                        // remove the hotel day from the owns
                        // because we've (found an entertainment to it/allocated an entertainment to it)
                        hotelOwns[hotelIndexOffset + j - 1]--;
                    }
                } else {
                    // on the other hand, if either first day or second has hotel reservations
                    if (isFirstDay != isLastDay) {
                        int day = isFirstDay ? firstHotelDay : lastHotelDay;
                        log.fine(EntertainmentLogPrefix + "First/Last Hotel Day: " + firstHotelDay + "/" + lastHotelDay);
                        log.fine(EntertainmentLogPrefix + "Day: " + day);
                        log.fine(EntertainmentLogPrefix + "Days: " + Arrays.toString(days.toArray()));
                        log.fine(EntertainmentLogPrefix + "Client Auctions: " + Arrays.toString(clientAuctions.toArray()));
                        try {
                            // this line sometimes causes index-out-of-exception
                            entertainmentAuction = clientAuctions.get(days.indexOf(day));
                            entertainmentAuction -= 16;
                            // either subtract from own entertainment
                            // or add new allocation
                            if (entertainmentOwns[entertainmentAuction] < 1) {
                                entertainmentAllocations[entertainmentAuction]++;
                            } else {
                                entertainmentOwns[entertainmentAuction]--;
                            }
                            // remove the hotel day from the owns
                            // because we've (found an entertainment to it/allocated an entertainment to it)
                            hotelOwns[hotelIndexOffset + day - 1]--;
                        } catch (Exception exp) {
                            log.severe(EntertainmentLogPrefix + "Exception: " + exp.toString());
                        }
                    }
                }
            }
        }

        // now after we've collected what the clients need, add them to the allocations
        // also, update the allocations of the bid list
        int allocation;
        for (int i = 0; i < 12; i++) {
            auction = i + 16;
            allocation = agent.getAllocation(auction) + entertainmentAllocations[i];
            agent.setAllocation(auction, allocation);
            entertainmentBidsAllocations[i] = allocation;
        }
        log.fine(EntertainmentLogPrefix + "Entertainment Allocations after Processing");
        log.fine(EntertainmentLogPrefix + "New Allocations: " + Arrays.toString(entertainmentAllocations));
        log.fine(EntertainmentLogPrefix + "Bid Allocations: " + Arrays.toString(entertainmentBidsAllocations));
    }

    /**
     * Check the status of the de-allocations (un-wanted entertainment tickets)
     * and see if a bid is need to be updated or not.
     */
    private void entertainmentDeAllocationsProcessor() {

        // try selling all the deAllocations we have
        int auction;
        int deAllocation;
        int bidDeAllocation;
        Quote quote;
        Bid oldBid;
        Bid newBid;

        log.fine(EntertainmentLogPrefix + "entertainmentDeAllocationsProcessor");

        for (int i = 0; i < 12; i++) {

            auction = i + 16;
            deAllocation = entertainmentDeAllocations[i];
            bidDeAllocation = entertainmentBidsDeAllocations[i];

            // if there is a de-allocation, create/update bid for it
            if (deAllocation > 0) {

                newBid = new Bid(auction);
                sellBidPrices[i] = 200;
                newBid.addBidPoint(-deAllocation, sellBidPrices[i]);
                agent.submitBid(newBid);

                // now move the de-allocations to the bids de-allocations list
                entertainmentDeAllocations[i] -= deAllocation;
                entertainmentBidsDeAllocations[i] += deAllocation;

            } else if (bidDeAllocation > 0 && sellBidPrices[i] > 60) {
                quote = agent.getQuote(auction);
                oldBid = quote.getBid();
                if (oldBid != null) {
                    newBid = new Bid(oldBid);
                    sellBidPrices[i] -= 2;
                    newBid.addBidPoint(-bidDeAllocation, sellBidPrices[i]);
                    agent.replaceBid(oldBid, newBid);
                }
            }
        }
    }

    /**
     * Read the status of the updated entertainment auction, process it and decide to add/update a bid
     * in order to cell/buy entertainment goods (tickets).
     *
     * @param auction
     * @param quote
     */
    private void entertainmentAuctionProcessor(int auction, Quote quote) {

        //FOR each entertainment ticket in possession
        //  Assign a pre-specified value to target;
        //  mean ← getMeanValue();
        //  M  ←  A*target + B*mean;
        //  IF M ≤ (1/2)*target OR M ≥ (3/2)*target THEN
        //      M  ←  relocateM();
        //  END IF
        //  V  ←  calcVal();
        //  IF V < V o  THEN V ← V o ; END IF
        // profit  ← ask– 2*V;
        // M t ← w(t)*M;
        // R a ← M;
        // R b ← 3*M/2;
        // R c  ← rand(M/2, M);
        // IF profit ≥ M t -R a  THEN sellTicket();
        // ELSE IF profit ≥ M t -R b  THEN ask (M t -R a +2*V);
        // ELSE ask (M t  - R c  +2*V);
        // END IF
        //END FOR

        // for all the allocations we have, bid on them
        // then move the allocations and save them in the entertainmentBids list

        Bid oldBid;
        Bid newBid;
        int allocation;
        int bidAllocation;

        log.fine(EntertainmentLogPrefix + "Processing for Auction: " + TACAgent.getAuctionTypeAsString(auction));

        // check if we can afford the ask price
        if (quote.getAskPrice() > 80) {
            log.fine(EntertainmentLogPrefix + "Ask Price is high, left the processing!");
            return;
        }

        // check if we still need more allocations
        allocation = agent.getAllocation(auction);
        bidAllocation = entertainmentBidsAllocations[auction - 16];
        log.fine(EntertainmentLogPrefix + "Allocation/bid allocation: " + allocation + "/" + bidAllocation);

        if (allocation > 0) {
            newBid = entertainmentNewBid(quote, allocation, auction);
            agent.submitBid(newBid);
            agent.setAllocation(auction, 0);
            entertainmentBidsAllocations[auction - 16] += allocation;
            log.fine(EntertainmentLogPrefix + "Entertainment submitting new bid for auction: " + agent.getAuctionTypeAsString(auction));
        } else if (bidAllocation > 0) {
            oldBid = agent.getBid(auction);
            if (oldBid != null) {
                newBid = entertainmentUpdatedBid(quote, allocation, auction);
                agent.replaceBid(oldBid, newBid);
                log.fine(EntertainmentLogPrefix + "Old Status: " + oldBid.getProcessingStateAsString());
                log.fine(EntertainmentLogPrefix + "Entertainment, replacing old bid with updated price for auction: " + agent.getAuctionTypeAsString(auction));

                //if (oldBidStatus == Bid.VALID) {
                //} else if (oldBidStatus == Bid.REJECTED) {
                //    newBid = entertainmentNewBid(quote, allocation, auction);
                //    agent.replaceBid(oldBid, newBid);
                //    log.fine(EntertainmentLogPrefix + "Entertainment Replacing old bid (rejected price not beaten) for auction: " + agent.getAuctionTypeAsString(auction));
                //}
            }
        }
    }

    /**
     * Create new entertainment bid with increased price (hopefully to beat the quote).
     *
     * @param quote
     * @param allocation
     * @param auction
     * @return
     */
    private Bid entertainmentNewBid(Quote quote, int allocation, int auction) {

        int margin = entertainmentBidMargin(auction);
        float newBidPrice = quote.getAskPrice() + margin;
        buyBidPrices[auction] = newBidPrice;

        Bid newBid;
        newBid = new Bid(auction);
        newBid.addBidPoint(allocation, newBidPrice);

        return newBid;
    }

    /**
     * Create an updated entertainment bid with the the ask price + margin (hopefully to beat the quote).
     *
     * @param quote
     * @param allocation
     * @param auction
     * @return
     */
    private Bid entertainmentUpdatedBid(Quote quote, int allocation, int auction) {

        int margin = entertainmentBidMargin(auction);

        // increase the price than the old one
        buyBidPrices[auction] = quote.getAskPrice() + margin;

        Bid updatedBid = new Bid(auction);
        updatedBid.addBidPoint(allocation, buyBidPrices[auction]);
        return updatedBid;
    }

    /**
     * The margin to be added in a new entertainment bid.
     *
     * @return
     */
    private int entertainmentBidMargin(int auction) {

        int margin;
        int bidPrice = (int) buyBidPrices[auction];

        // margin should increase the bid price
        margin = (int) (bidPrice + 1);
        margin = margin < 2 ? 2 : margin;

        return margin;
    }

    /**
     * Return list of entertainment types the client preferred. Ascending order according to the preferences.
     *
     * @param client
     * @return
     */
    private ArrayList<Integer> entertainmentClientPreferredTypes(int client) {

        int e1 = clientPreferences[client][TACAgent.E1];
        int e2 = clientPreferences[client][TACAgent.E2];
        int e3 = clientPreferences[client][TACAgent.E3];

        final int[] types = {TACAgent.TYPE_ALLIGATOR_WRESTLING, TACAgent.TYPE_AMUSEMENT, TACAgent.TYPE_MUSEUM};
        Integer[] preferredTypes;

        if (e1 >= e2 && e2 >= e3) {
            preferredTypes = new Integer[]{types[0], types[1], types[2]};
        } else if (e1 >= e3 && e3 >= e2) {
            preferredTypes = new Integer[]{types[0], types[2], types[1]};
        } else if (e2 >= e1 && e1 >= e3) {
            preferredTypes = new Integer[]{types[1], types[0], types[2]};
        } else if (e2 >= e3 && e3 >= e1) {
            preferredTypes = new Integer[]{types[1], types[2], types[0]};
        } else if (e3 >= e1 && e1 >= e2) {
            preferredTypes = new Integer[]{types[2], types[0], types[1]};
        } else if (e3 >= e2 && e2 >= e1) {
            preferredTypes = new Integer[]{types[2], types[1], types[0]};
        } else {
            preferredTypes = new Integer[0];
            log.severe("!!!!!!!!#######!!!!!! This is impossible logical case, each of the entertainment preference must be not equal to the others!");
        }

        ArrayList<Integer> list = new ArrayList<Integer>();
        list.addAll(Arrays.asList(preferredTypes));
        return list;
    }

    /**
     * Return list of entertainment types the client preferred. Ascending order according to the preferences.
     *
     * @param client
     * @param duration
     * @return
     */
    private ArrayList<Integer> entertainmentClientPreferredTypes(int client, int duration) {
        ArrayList<Integer> preferredTypes = entertainmentClientPreferredTypes(client);
        if (duration < 3) {
            preferredTypes = new ArrayList<Integer>(preferredTypes.subList(0, duration));
        }
        return preferredTypes;
    }

    /**
     * Get the hotel days of the given entertainment auctions.
     *
     * @param auctions
     * @return
     */
    private ArrayList<Integer> entertainmentAuctionsToHotelDays(ArrayList<Integer> auctions) {

        int size = auctions.size();
        ArrayList<Integer> days = new ArrayList<Integer>(size);
        for (int i = 0; i < size; i++) {
            days.add(entertainmentAuctionToHotelDay(auctions.get(i)));
        }
        return days;
    }

    /**
     * Get the hotel day of the given entertainment auction.
     *
     * @return
     */
    private int entertainmentAuctionToHotelDay(int auction) {

        auction -= 16;
        int day = (auction % 4) + 1;
        return day;
    }

    /**
     * Collects the prices of the entertainment auctions (12 auctions).
     */
    private void entertainmentPricesCollector(Quote quote) {

        float askPrice = quote.getAskPrice();
        float bidPrice = quote.getBidPrice();
        long time = agent.getGameTime();

        int auction = quote.getAuction() - 16;
        entertainmentPrices.get(auction).add(new DoublePricePoint(askPrice, bidPrice, time));
    }

    /**
     * Print all the entertainment prices.
     */
    private void entertainmentPrintPrices() {

        ArrayList<DoublePricePoint> _prices;
        DoublePricePoint _price;

        log.fine(LogPrefix + "Start Printing All Entertainment Ask/Bid Prices");
        for (int i = 0; i < entertainmentPrices.size(); i++) {
            log.fine(LogPrefix + "Printing (time, ask price, price) for " + agent.getAuctionTypeAsString(i + 16) + " :");
            _prices = entertainmentPrices.get(i);
            for (int j = 0; j < _prices.size(); j++) {
                _price = _prices.get(j);
                log.fine(LogPrefix + _price.seconds + ", " + _price.ask + ", " + _price.bid);
            }
        }
        log.fine(LogPrefix + "Finish Printing All Entertainment Ask/Bid Prices");
    }

    // endregion Private Methods [Entertainment]

    //region Private Methods [Entertainment Deprecated]

    /**
     * Set the allocations for the entertainment auctions according to the client preferences.
     */
    @Deprecated
    private void entertainmentSetAllocations_() {

        /*int auction;
        int allocation;
        int inFlightDay;
        int outFlightDay;
        int entertainmentType;

        // loop on the 8 clients and get their preferences
        // note: we have 8 clients for each game
        for (int i = 0; i < 8; i++) {

            // get flight preferences (arrival and departure days)
            inFlightDay = clientPreferences[i][TACAgent.ARRIVAL];
            outFlightDay = clientPreferences[i][TACAgent.DEPARTURE];
            entertainmentType = -1;
            while ((entertainmentType = nextPreferredEntertainmentType(i, entertainmentType)) > 0) {
                auction = bestEntertainmentDay(inFlightDay, outFlightDay, entertainmentType);
                allocation = agent.getAllocation(auction);
                allocation++;
                agent.setAllocation(auction, allocation);
            }
        }*/
    }

    /**
     * Read the status of the current entertainment auctions, process it and decide to add/update a bid
     * in order to cell/buy entertainment goods (tickets).
     *
     * @param auction
     */
    @Deprecated
    private void entertainmentAuctionsProcessor_(int auction) {

        /*int alloc = agent.getAllocation(auction) - agent.getOwn(auction);
        if (alloc != 0) {
            Bid bid = new Bid(auction);
            if (alloc < 0) {
                buyBidPrices[auction] = 200f - (agent.getGameTime() * 120f) / 720000;
            } else {
                buyBidPrices[auction] = 50f + (agent.getGameTime() * 100f) / 720000;
            }
            bid.addBidPoint(alloc, buyBidPrices[auction]);
            agent.submitBid(bid);

            log.fine("submitting bid with alloc=" + agent.getAllocation(auction) + " own=" + agent.getOwn(auction));
        }*/
    }

    /**
     * Get the best entertainment day
     */
    @Deprecated
    private int bestEntertainmentDay_(int inFlightDay, int outFlightDay, int type) {

        int auction;
        for (int i = inFlightDay; i < outFlightDay; i++) {
            auction = TACAgent.getAuctionFor(TACAgent.CAT_ENTERTAINMENT, type, i);
            if (agent.getAllocation(auction) < agent.getOwn(auction)) {
                return auction;
            }
        }
        // if no left, just take the first...
        return TACAgent.getAuctionFor(TACAgent.CAT_ENTERTAINMENT, type, inFlightDay);

    }

    /**
     * Get the next entertainment type for the client
     * Choose according to his preferences
     *
     * @param client
     * @param lastType
     * @return
     */
    @Deprecated
    private int nextPreferredEntertainmentType_(int client, int lastType) {

        int e1 = clientPreferences[client][TACAgent.E1];
        int e2 = clientPreferences[client][TACAgent.E2];
        int e3 = clientPreferences[client][TACAgent.E3];

        // At least buy what each agent wants the most!!!
        if ((e1 > e2) && (e1 > e3) && lastType == -1) {
            return TACAgent.TYPE_ALLIGATOR_WRESTLING;
        }
        if ((e2 > e1) && (e2 > e3) && lastType == -1) {
            return TACAgent.TYPE_AMUSEMENT;
        }
        if ((e3 > e1) && (e3 > e2) && lastType == -1) {
            return TACAgent.TYPE_MUSEUM;
        }
        return -1;
    }

    //endregion Private Methods [Entertainment Deprecated]

    // region Private Methods [Misc.]

    /**
     * Send a bids to the server.
     */
    @Deprecated
    private void sendBids_() {
        for (int i = 0, n = agent.getAuctionNo(); i < n; i++) {
            int alloc = agent.getAllocation(i) - agent.getOwn(i);
            float price = -1f;
            switch (agent.getAuctionCategory(i)) {
                case TACAgent.CAT_FLIGHT:
                    if (alloc > 0) {
                        price = 1000;
                    }
                    break;
                case TACAgent.CAT_HOTEL:
                    if (alloc > 0) {
                        price = 200;
                        buyBidPrices[i] = 200f;
                    }
                    break;
                case TACAgent.CAT_ENTERTAINMENT:
                    if (alloc < 0) {
                        price = 200;
                        buyBidPrices[i] = 200f;
                    } else if (alloc > 0) {
                        price = 50;
                        buyBidPrices[i] = 50f;
                    }
                    break;
                default:
                    break;
            }
            if (price > 0) {
                Bid bid = new Bid(i);
                bid.addBidPoint(alloc, price);
                if (DEBUG) {
                    log.fine("submitting bid with alloc=" + agent.getAllocation(i) + " own=" + agent.getOwn(i));
                }
                agent.submitBid(bid);
            }
        }
    }

    /**
     * Get hotel type [Cheap/Good] according to the hotel value of the given client.
     */
    private int getHotelType(int client) {

        int value = clientPreferences[client][TACAgent.HOTEL_VALUE];
        int type = (value > 70) ? TACAgent.TYPE_GOOD_HOTEL : TACAgent.TYPE_CHEAP_HOTEL;
        return type;
    }

    /**
     * Is auction clear (open for bidding).
     *
     * @param status
     * @return
     */
    private boolean isAuctionClear(int status) {

        return status == Quote.AUCTION_INTERMEDIATE_CLEAR || status == Quote.AUCTION_FINAL_CLEAR;
    }

    /**
     * Printing client preferences.
     */
    private void printClientPreferences() {
        log.fine(LogPrefix + "Printing Client Preferences");
        for (int i = 0; i < 8; i++) {
            log.fine(LogPrefix + "Client " + (i + 1) + " : " + Arrays.toString(clientPreferences[i]));
        }
        log.fine(LogPrefix + "Finish Printing Client Preferences");
    }

    /**
     * Get array of prices from array list of price points.
     *
     * @param list
     * @return
     */
    private double[] listToArray(ArrayList<PricePoint> list) {

        int length = list.size();
        double[] array = new double[length];
        PricePoint price;
        for (int j = 0; j < length; j++) {
            price = list.get(j);
            array[j] = price.value;
        }

        return array;
    }

    /**
     * Get quantity of bid from bid string.
     *
     * @param bidString
     * @return
     */
    private int quantityFromBidString(String bidString) {

        String quantityString = bidString.split(" ")[0];
        quantityString = quantityString.substring(2);
        int quantity = Integer.parseInt(quantityString);
        return quantity;
    }

    // endregion Private Methods
}