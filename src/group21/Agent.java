package group21;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.EndNegotiation;
import genius.core.actions.Offer;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.EvaluatorDiscrete;


public class Agent extends AbstractNegotiationParty {

    private static double MINIMUM_TARGET;
    private Bid lastOffer;
    private HashMap<Integer, HashMap<String, Integer>> frequencyTable = new HashMap<>();
    private HashMap<String, Integer> options = new HashMap<>();
    private int numberOfBids = 0;


    /**
     * Initializes a new instance of the agent.
     */
    @Override
    public void init(NegotiationInfo info) {
        super.init(info);
        System.out.println("testing1234");

        AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
        AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;

        List<Issue> issues = additiveUtilitySpace.getDomain().getIssues();

        for (Issue issue : issues) {
            int issueNumber = issue.getNumber();
            System.out.println(">> " + issue.getName() + " weight: " + additiveUtilitySpace.getWeight(issueNumber));

            // Assuming that issues are discrete only
            IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
            EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issueNumber);

            for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
                System.out.println(valueDiscrete.getValue());
                System.out.println("Evaluation(getValue): " + evaluatorDiscrete.getValue(valueDiscrete));
                try {
                    System.out.println("Evaluation(getEvaluation): " + evaluatorDiscrete.getEvaluation(valueDiscrete));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                options.put(valueDiscrete.getValue(), 0);
            }

            frequencyTable.put(issueNumber, options);
            MINIMUM_TARGET = getUtility(getMaxUtilityBid());

        }
    }

    /**
     * Makes a random offer above the minimum utility target
     * Accepts everything above the reservation value at the end of the negotiation; or breaks off otherwise.
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) {
        // Check for acceptance if we have received an offer
        if (lastOffer != null) {
            double threshold = (utilitySpace.getUtility(getMaxUtilityBid()) + utilitySpace.getUtility(getMinUtilityBid())) / 2;
            double timeConcession = (1 - getTimeLine().getTime()) * getUtility(getMaxUtilityBid());
            MINIMUM_TARGET = Math.max(threshold,timeConcession);
            if (timeline.getTime() >= 0.9) {
                if (getUtility(lastOffer) >= threshold) {
                    return new Accept(getPartyId(), lastOffer);
                } else {
                    return new EndNegotiation(getPartyId());
                }
            }
        }


        return new Offer(getPartyId(), paretoEffecientOffer(15));
    }

    private Bid generateRandomBidAboveTarget() {
        Bid randomBid;
        double util;
        int i = 0;
        // try 100 times to find a bid under the target utility
        do {
            randomBid = generateRandomBid();
            util = utilitySpace.getUtility(randomBid);
        }
        while (util < MINIMUM_TARGET && i++ < 100);
        return randomBid;
    }

    /**
     * Remembers the offers received by the opponent.
     */
    @Override
    public void receiveMessage(AgentID sender, Action action) {
        numberOfBids += 1;
        if (action instanceof Offer) {
            lastOffer = ((Offer) action).getBid();
            List<Issue> offerIssues = lastOffer.getIssues();

            for (Issue iss : offerIssues) {
                int issueNum = iss.getNumber();
                String option = ((ValueDiscrete) lastOffer.getValue(issueNum)).getValue();
                int count = frequencyTable.get(issueNum).get(option);
                frequencyTable.get(issueNum).put(option, count + 1);
            }
            generateNashBargainingValue(15);
        }
    }


    private double[] getOptionsRank(Bid bid, List<Issue> issues) {
        double[] ranks = new double[issues.size()];
        int rank = 0;
        for (int i = 0; i < issues.size(); i++) {
            Issue issue = issues.get(i);
            int issueNumber = issue.getNumber();
            HashMap<String, Integer> options = frequencyTable.get(issueNumber);
            double numOptions = options.size();

            String bidOption = ((ValueDiscrete) bid.getValue(issueNumber)).getValue();
            int bidOptionValue = options.get(bidOption);

            for (String option : options.keySet()) {
                if (options.get(option) >= bidOptionValue)
                    rank += 1;
            }

            ranks[i] = (numOptions - rank + 1) / numOptions;

        }
        return ranks;
    }

    private double[] normalizedWeights(List<Issue> issues) {

        double[] weights = new double[issues.size()];

        for (int i = 0; i < issues.size(); i++) {
            List<Integer> optionFrequency = new ArrayList<>(frequencyTable.get(issues.get(i).getNumber()).values());
            double weight = 0;
            for (Integer option : optionFrequency)
                weight += (Math.pow(option, 2) / Math.pow(numberOfBids, 2));

            weights[i] = weight;
        }
        double weightedSum = Arrays.stream(weights).sum();
        double[] normalisedWeights = new double[issues.size()];
        for (int i = 0; i < weights.length; i++)
            normalisedWeights[i] = weights[i] / weightedSum;

        return normalisedWeights;
    }

    private double offerValuation(Bid offer) {

        List<Issue> issues = offer.getIssues();
        double predictedValue = 0;
        double[] ranks = getOptionsRank(offer, issues);
        double[] weights = normalizedWeights(issues);
        for (int i = 0; i < ranks.length; i++) {
            predictedValue += ranks[i] * weights[i];
        }
        return predictedValue;

    }

    private Bid paretoEffecientOffer(int n) {

        double opponentUtil = 0;
        Bid paretoBid = null;

        for (int i = 0; i < n; i++) {
            Bid randomBid;
            double util;
            do {
                randomBid = generateRandomBid();
                util = utilitySpace.getUtility(randomBid);

                // Estimate opponent utility
                opponentUtil = offerValuation(randomBid);
            }
            while (util < MINIMUM_TARGET);
            if (util > opponentUtil) {
                paretoBid = randomBid;
            }
        }
        return paretoBid;

    }

    private Bid getMaxUtilityBid() {
        try {
            return utilitySpace.getMaxUtilityBid();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Bid getMinUtilityBid() {
        try {
            return utilitySpace.getMinUtilityBid();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private double generateNashBargainingValue(int n){
        double max = Double.MIN_VALUE;
        double nash = MINIMUM_TARGET;
        Bid randomBid;
            for (int i = 0; i < n; i++) {
                randomBid = generateRandomBid();
                double product = getUtility(randomBid) * offerValuation(randomBid);
                if (product > max) {
                    max = product;
                    nash = getUtility(randomBid);
                }
            }
        return nash;
    }





    @Override
    public String getDescription() {
        return "Places bids based on Johnny Black Oppenent Model";
    }

    /**
     * This stub can be expanded to deal with preference uncertainty in a more sophisticated way than the default behavior.
     */
    @Override
    public AbstractUtilitySpace estimateUtilitySpace() {
        return super.estimateUtilitySpace();
    }


}
