package multipartyexample;


import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

import negotiator.AgentID;
import negotiator.Bid;
import negotiator.Deadline;
import negotiator.actions.Accept;
import negotiator.actions.Action;
import negotiator.actions.Offer;
import negotiator.issue.Issue;
import negotiator.issue.IssueDiscrete;
import negotiator.issue.IssueInteger;
import negotiator.issue.IssueReal;
import negotiator.issue.Value;
import negotiator.issue.ValueInteger;
import negotiator.issue.ValueReal;
import negotiator.bidding.BidDetails;
import negotiator.boaframework.SortedOutcomeSpace;
import negotiator.parties.AbstractNegotiationParty;
import negotiator.session.TimeLineInfo;
import negotiator.utility.AbstractUtilitySpace;

public class Groupn extends AbstractNegotiationParty {

	private int timeSteps = 0;
	private int opponentCount = 0;
	private double discountFactor = 0;
	private double minimumBidUtility = 0.0;
	private Bid[] opponentBids = new Bid[100];
	private static double currentMinimumBidUtility = 0.99;

	@Override
	public void init(AbstractUtilitySpace utilSpace, Deadline dl,
	TimeLineInfo tl, long randomSeed, AgentID agentId) {

		super.init(utilSpace, dl, tl, randomSeed, agentId);
		discountFactor = utilSpace.getDiscountFactor();
		minimumBidUtility = utilSpace.getReservationValueUndiscounted();
		minimumBidUtility = 0.9;
		for(int i = 0;i < 100; ++i) opponentBids[i] = null;
	}


	/**
	 * Each round this method gets called and ask you to accept or offer. The
	 * first party in the first round is a bit different, it can only propose an
	 * offer.
	 *
	 * @param validActions
	 *            Either a list containing both accept and offer or only offer.
	 * @return The chosen action.
	 */
	@Override
	public Action chooseAction(List<Class<? extends Action>> validActions) {
		Action action = null;
		try {
			timeSteps += 1;
			if (currentMinimumBidUtility > minimumBidUtility)
				currentMinimumBidUtility = 1-(timeSteps)*0.01;

			action = chooseRandomBidAction();     //TODO
			Bid myBid = ((Offer) action).getBid();
			double myOfferedUtil = getUtility(myBid);


			for(int i=1; i<=opponentCount; ++i) {
				if(opponentBids[i] == null) continue;
				double offeredUtilFromOpponent = getUtility(opponentBids[i]);
				if (isAcceptable(offeredUtilFromOpponent, myOfferedUtil,timeSteps))   //TODO
					action = new Accept();
			}

		} catch (Exception e) {
			System.out.println("Exception in ChooseAction:" + e.getMessage());
			action = new Accept(); // best guess if things go wrong.
		}
		return action;
	}

	/**
	 * All offers proposed by the other parties will be received as a message.
	 * You can use this information to your advantage, for example to predict
	 * their utility.
	 *
	 * @param sender
	 *            The party that did the action. Can be null.
	 * @param action
	 *            The action that party did.
	 */
	@Override
	public void receiveMessage(AgentID sender, Action action) {
		super.receiveMessage(sender, action);
		if(sender != null) {
			int senderId = Integer.parseInt(sender.toString().split(" ", 2)[1]);
			if (action instanceof Offer)
				opponentBids[senderId] = ((Offer) action).getBid();
			else
				opponentBids[senderId] = null;
		}
		else
			opponentCount = Integer.parseInt(action.toString().split(":", 2)[1]);
	}


	@Override
	public String getDescription() {
		return "example party group N";
	}

	/**
	 * Wrapper for getRandomBid, for convenience.
	 *
	 * @return new Action(Bid(..)), with bid utility > MINIMUM_BID_UTIL. If a
	 *         problem occurs, it returns an Accept() action.
	 */
	private Action chooseRandomBidAction() {
		Bid nextBid = null;
		try {
			nextBid = getRandomBid();
		} catch (Exception e) {
			System.out.println("Problem with received bid:" + e.getMessage()
					+ ". cancelling bidding");
		}
		if (nextBid == null)
			return (new Accept());
		return (new Offer(nextBid));
	}

	/**
	 * @return a random bid with high enough utility value.
	 * @throws Exception
	 *             if we can't compute the utility (eg no evaluators have been
	 *             set) or when other evaluators than a DiscreteEvaluator are
	 *             present in the util space.
	 */
	private Bid getRandomBid() throws Exception {
		HashMap<Integer, Value> values = new HashMap<Integer, Value>(); // pairs
																		// <issuenumber,chosen
																		// value
																		// string>
		ArrayList<Issue> issues = utilitySpace.getDomain().getIssues();
		Random randomnr = new Random();

		// create a random bid with utility>MINIMUM_BID_UTIL.
		// note that this may never succeed if you set MINIMUM too high!!!
		// in that case we will search for a bid till the time is up (3 minutes)
		// but this is just a simple agent.
		Bid bid = null;
		Bid bestBid = null;
		double bestUtil=0.0;
		do {
			for (Issue lIssue : issues) {
				switch (lIssue.getType()) {
				case DISCRETE:
					IssueDiscrete lIssueDiscrete = (IssueDiscrete) lIssue;
					int optionIndex = randomnr.nextInt(lIssueDiscrete
							.getNumberOfValues());
					values.put(lIssue.getNumber(),
							lIssueDiscrete.getValue(optionIndex));
					break;
				case REAL:
					IssueReal lIssueReal = (IssueReal) lIssue;
					int optionInd = randomnr.nextInt(lIssueReal
							.getNumberOfDiscretizationSteps() - 1);
					values.put(
							lIssueReal.getNumber(),
							new ValueReal(lIssueReal.getLowerBound()
									+ (lIssueReal.getUpperBound() - lIssueReal
											.getLowerBound())
									* (double) (optionInd)
									/ (double) (lIssueReal
											.getNumberOfDiscretizationSteps())));
					break;
				case INTEGER:
					IssueInteger lIssueInteger = (IssueInteger) lIssue;
					int optionIndex2 = lIssueInteger.getLowerBound()
							+ randomnr.nextInt(lIssueInteger.getUpperBound()
									- lIssueInteger.getLowerBound());
					values.put(lIssueInteger.getNumber(), new ValueInteger(
							optionIndex2));
					break;
				default:
					throw new Exception("issue type " + lIssue.getType()
							+ " not supported by SimpleAgent2");
				}
			}
			bid = new Bid(utilitySpace.getDomain(), values);

			if (getUtility(bid)>bestUtil)
				{
					bestUtil=getUtility(bid);
					bestBid=bid;
				}
		} while (getUtility(bestBid) < currentMinimumBidUtility);

		return bestBid;
	}


	private boolean isAcceptable(double offeredUtilFromOpponent,
			double myOfferedUtil, int time) throws Exception {
		double P = Paccept(offeredUtilFromOpponent, time);
		if (P > Math.random())
			return true;
		return false;
	}

	double Paccept(double u, int t1) throws Exception {
		if (u < 0 || u > 1.05)
			throw new Exception("utility " + u + " outside [0,1]");
		// if (t1 < 0 || t1 > 1)
		// 	throw new Exception("time " + t1 + " outside [0,1]");
		if (u > 1.)
			u = 1;
		if(u>=currentMinimumBidUtility-0.0005)
			return 1;
		return 0;
	}


}
