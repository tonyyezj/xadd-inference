package tskill.jskills.trueskill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tskill.jskills.GameInfo;
import tskill.jskills.IPlayer;
import tskill.jskills.ITeam;
import tskill.jskills.Rating;
import tskill.jskills.factorgraphs.Factor;
import tskill.jskills.factorgraphs.FactorGraph;
import tskill.jskills.factorgraphs.FactorGraphLayerBase;
import tskill.jskills.factorgraphs.FactorList;
import tskill.jskills.factorgraphs.KeyedVariable;
import tskill.jskills.factorgraphs.Schedule;
import tskill.jskills.factorgraphs.ScheduleSequence;
import tskill.jskills.numerics.GaussianDistribution;
import tskill.jskills.trueskill.layers.IteratedTeamDifferencesInnerLayer;
import tskill.jskills.trueskill.layers.PlayerPerformancesToTeamPerformancesLayer;
import tskill.jskills.trueskill.layers.PlayerPriorValuesToSkillsLayer;
import tskill.jskills.trueskill.layers.PlayerSkillsToPerformancesLayer;
import tskill.jskills.trueskill.layers.TeamDifferencesComparisonLayer;
import tskill.jskills.trueskill.layers.TeamPerformancesToTeamPerformanceDifferencesLayer;

public class TrueSkillFactorGraph extends FactorGraph<TrueSkillFactorGraph>
{
    private final List<FactorGraphLayerBase<GaussianDistribution>> _Layers = new ArrayList<FactorGraphLayerBase<GaussianDistribution>>();
    private final PlayerPriorValuesToSkillsLayer _PriorLayer;

    public TrueSkillFactorGraph(GameInfo gameInfo, Collection<ITeam> teams, int[] teamRanks)
    {
        _PriorLayer = new PlayerPriorValuesToSkillsLayer(this, teams);
        setGameInfo(gameInfo);

        _Layers.add(_PriorLayer);
        _Layers.add(new PlayerSkillsToPerformancesLayer(this));
        _Layers.add(new PlayerPerformancesToTeamPerformancesLayer(this));
        _Layers.add(new IteratedTeamDifferencesInnerLayer(
                              this,
                              new TeamPerformancesToTeamPerformanceDifferencesLayer(this),
                              new TeamDifferencesComparisonLayer(this, teamRanks)));
    }

    private GameInfo gameInfo;
    public GameInfo getGameInfo() { return gameInfo; }
    private void setGameInfo(GameInfo info) { gameInfo = info; } 

    public void BuildGraph()
    {
        Object lastOutput = null;

        for(FactorGraphLayerBase<GaussianDistribution> currentLayer :_Layers)
        {
            if (lastOutput != null)
            {
                currentLayer.SetRawInputVariablesGroups(lastOutput);
            }

            currentLayer.BuildLayer();

            lastOutput = currentLayer.GetRawOutputVariablesGroups();
        }
    }

    public void RunSchedule()
    {
        Schedule<GaussianDistribution> fullSchedule = CreateFullSchedule();
        @SuppressWarnings("unused") // TODO Maybe something can be done w/ this?
        double fullScheduleDelta = fullSchedule.visit();
    }

    public double GetProbabilityOfRanking()
    {
        FactorList<GaussianDistribution> factorList = new FactorList<GaussianDistribution>();

        for(FactorGraphLayerBase<GaussianDistribution> currentLayer :_Layers)
        {
            for(Factor<GaussianDistribution> currentFactor :currentLayer.getUntypedFactors())
            {
                factorList.addFactor(currentFactor);
            }
        }

        double logZ = factorList.getLogNormalization();
        return Math.exp(logZ);
    }

    private Schedule<GaussianDistribution> CreateFullSchedule()
    {
        List<Schedule<GaussianDistribution>> fullSchedule = new ArrayList<Schedule<GaussianDistribution>>();

        for(FactorGraphLayerBase<GaussianDistribution> currentLayer :_Layers)
        {
            Schedule<GaussianDistribution> currentPriorSchedule = currentLayer.createPriorSchedule();
            if (currentPriorSchedule != null)
            {
                fullSchedule.add(currentPriorSchedule);
            }
        }

        // Getting as a list to use reverse()
        List<FactorGraphLayerBase<GaussianDistribution>> allLayers = new ArrayList<FactorGraphLayerBase<GaussianDistribution>>(_Layers);
        Collections.reverse(allLayers);

        for(FactorGraphLayerBase<GaussianDistribution> currentLayer : allLayers)
        {
            Schedule<GaussianDistribution> currentPosteriorSchedule = currentLayer.createPosteriorSchedule();
            if (currentPosteriorSchedule != null)
            {
                fullSchedule.add(currentPosteriorSchedule);
            }
        }

        return new ScheduleSequence<GaussianDistribution>("Full schedule", fullSchedule);
    }

    public Map<IPlayer, Rating> GetUpdatedRatings()
    {
        Map<IPlayer, Rating> result = new HashMap<IPlayer, Rating>();
        for(List<KeyedVariable<IPlayer, GaussianDistribution>> currentTeam : _PriorLayer.getOutputVariablesGroups())
        {
            for(KeyedVariable<IPlayer, GaussianDistribution> currentPlayer : currentTeam)
            {
                result.put(currentPlayer.getKey(), new Rating(currentPlayer.getValue().getMean(),
                                                       currentPlayer.getValue().getStandardDeviation()));
            }
        }

        return result;
    }
}