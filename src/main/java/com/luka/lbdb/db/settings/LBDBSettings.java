package com.luka.lbdb.db.settings;

import com.luka.lbdb.metadataManagement.MetadataManager;
import com.luka.lbdb.planning.planner.plannerDefinitions.QueryPlanner;
import com.luka.lbdb.planning.planner.plannerDefinitions.UpdatePlanner;
import com.luka.lbdb.planning.planner.plannerTypes.BasicUpdatePlanner;
import com.luka.lbdb.planning.planner.plannerTypes.BetterQueryPlanner;
import com.luka.lbdb.records.RecordId;

import java.util.Map;

/// Settings class for changing the instantiation parameters
/// of the system, useful for tests. The defaults are pretty good
/// for a working database.
public class LBDBSettings {
    public boolean UNDO_ONLY_RECOVERY = true;
    public int BLOCK_SIZE = 4096;
    public int BUFFER_POOL_SIZE = 128;
    public BufferStrategy bufferStrategy = BufferStrategy.LRU;
    public String LOG_FILE = "log_file";
    public QueryPlannerType queryPlannerType = QueryPlannerType.BETTER; // todo change default when heuristic is implemented
    public UpdatePlannerType updatePlannerType = UpdatePlannerType.BASIC;

    /// @return The query planner object according to the set setting.
    public QueryPlanner getQueryPlanner(MetadataManager metadataManager) {
        return switch (queryPlannerType) {
            case BETTER -> new BetterQueryPlanner(metadataManager);
            case HEURISTIC -> throw new UnsupportedOperationException("implement heuristic planner");
        };
    }

    /// @return The update planner object according to the set setting.
    public UpdatePlanner getUpdatePlanner(MetadataManager metadataManager, Map<String, RecordId> lastInsertions) {
        return switch (updatePlannerType) {
            case BASIC -> new BasicUpdatePlanner(metadataManager, lastInsertions);
        };
    }
}
