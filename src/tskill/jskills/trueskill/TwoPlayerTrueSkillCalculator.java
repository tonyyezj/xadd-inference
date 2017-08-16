package tskill.jskills.trueskill;

import static tskill.jskills.numerics.MathUtils.square;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import tskill.jskills.GameInfo;
import tskill.jskills.Guard;
import tskill.jskills.IPlayer;
import tskill.jskills.ITeam;
import tskill.jskills.PairwiseComparison;
import tskill.jskills.RankSorter;
import tskill.jskills.numerics.Range;
import tskill.jskills.Rating;
import tskill.jskills.SkillCalculator;

/**
 * Calculates the new ratings for only two players.
 * <remarks>
 * When you only have two players, a lot of the math simplifies. The main purpose of this class
 * is to show the bare minimum of what a TrueSkill implementation should have.
 * </remarks>
 */
public class TwoPlayerTrueSkillCalculator extends SkillCalculator
{
    public TwoPlayerTrueSkillCalculator()
    {
        super(EnumSet.noneOf(SupportedOptions.class), Range.<ITeam>exactly(2), Range.<IPlayer>exactly(1));
    }

    @Override
    public Map<IPlayer, Rating> calculateNewRatings(GameInfo gameInfo, Collection<ITeam> teams, int... teamRanks)
    {
        // Basic argument checking
        Guard.argumentNotNull(gameInfo, "gameInfo");
        validateTeamCountAndPlayersCountPerTeam(teams);

        // Make sure things are in order
        List<ITeam> teamsl = RankSorter.sort(teams, teamRanks);

        // Since we verified that each team has one player, we know the player is the first one
        ITeam winningTeam = teamsl.get(0);
        IPlayer winner = winningTeam.keySet().iterator().next();
        Rating winnerPreviousRating = winningTeam.get(winner);

        Map<IPlayer, Rating> losingTeam = teamsl.get(1);
        IPlayer loser = losingTeam.keySet().iterator().next();
        Rating loserPreviousRating = losingTeam.get(loser);

        boolean wasDraw = (teamRanks[0] == teamRanks[1]);

        Map<IPlayer, Rating> results = new HashMap<IPlayer, Rating>();
        results.put(winner, CalculateNewRating(gameInfo, winnerPreviousRating, loserPreviousRating,
                                             wasDraw ? PairwiseComparison.DRAW : PairwiseComparison.WIN));
        results.put(loser, CalculateNewRating(gameInfo, loserPreviousRating, winnerPreviousRating,
                                            wasDraw ? PairwiseComparison.DRAW : PairwiseComparison.LOSE));

        // And we're done!
        return results;
    }

    private static Rating CalculateNewRating(GameInfo gameInfo, Rating selfRating, Rating opponentRating,
                                             PairwiseComparison comparison)
    {
        double drawMargin = DrawMargin.GetDrawMarginFromDrawProbability(gameInfo.getDrawProbability(), gameInfo.getBeta());

        double c =
            Math.sqrt(
                square(selfRating.getStandardDeviation())
                +
                square(opponentRating.getStandardDeviation())
                +
                2*square(gameInfo.getBeta()));

        double winningMean = selfRating.getMean();
        double losingMean = opponentRating.getMean();

        switch (comparison)
        {
            case WIN: case DRAW: /* NOP */ break;
            case LOSE:
                winningMean = opponentRating.getMean();
                losingMean = selfRating.getMean();
                break;
        }

        double meanDelta = winningMean - losingMean;

        double v;
        double w;
        double rankMultiplier;

        if (comparison != PairwiseComparison.DRAW)
        {
            // non-draw case
            v = TruncatedGaussianCorrectionFunctions.VExceedsMargin(meanDelta, drawMargin, c);
            w = TruncatedGaussianCorrectionFunctions.WExceedsMargin(meanDelta, drawMargin, c);
            rankMultiplier = comparison.multiplier;
        }
        else
        {
            v = TruncatedGaussianCorrectionFunctions.VWithinMargin(meanDelta, drawMargin, c);
            w = TruncatedGaussianCorrectionFunctions.WWithinMargin(meanDelta, drawMargin, c);
            rankMultiplier = 1;
        }

        double meanMultiplier = (square(selfRating.getStandardDeviation()) + square(gameInfo.getDynamicsFactor()))/c;

        double varianceWithDynamics = square(selfRating.getStandardDeviation()) + square(gameInfo.getDynamicsFactor());
        double stdDevMultiplier = varianceWithDynamics/square(c);

        double newMean = selfRating.getMean() + (rankMultiplier*meanMultiplier*v);
        double newStdDev = Math.sqrt(varianceWithDynamics*(1 - w*stdDevMultiplier));

        return new Rating(newMean, newStdDev);
    }

    @Override
    public double calculateMatchQuality(GameInfo gameInfo, Collection<ITeam> teams)
    {
        Guard.argumentNotNull(gameInfo, "gameInfo");
        validateTeamCountAndPlayersCountPerTeam(teams);

        Iterator<ITeam> teamIt = teams.iterator();
        
        Rating player1Rating = teamIt.next().values().iterator().next();
        Rating player2Rating = teamIt.next().values().iterator().next();

        // We just use equation 4.1 found on page 8 of the TrueSkill 2006 paper:
        double betaSquared = square(gameInfo.getBeta());
        double player1SigmaSquared = square(player1Rating.getStandardDeviation());
        double player2SigmaSquared = square(player2Rating.getStandardDeviation());

        // This is the square root part of the equation:
        double sqrtPart =
            Math.sqrt(
                (2*betaSquared)
                /
                (2*betaSquared + player1SigmaSquared + player2SigmaSquared));

        // This is the exponent part of the equation:
        double expPart =
            Math.exp(
                (-1*square(player1Rating.getMean() - player2Rating.getMean()))
                /
                (2*(2*betaSquared + player1SigmaSquared + player2SigmaSquared)));

        return sqrtPart*expPart;
    }
}