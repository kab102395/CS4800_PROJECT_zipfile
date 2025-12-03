-- game_stats.lua
-- Unified game statistics tracking module
-- Tracks scores, achievements, and game metrics for submission

local game_stats = {}

-- Create a new game stats tracker
function game_stats.new(game_name)
    local stats = {
        game_name = game_name,
        start_time = os.time(),
        final_score = 0,
        level = 1,
        duration = 0,
        accuracy = 0,
        kills = 0,
        deaths = 0,
        shots_fired = 0,
        shots_hit = 0,
        time_elapsed = 0,
        custom_metrics = {}
    }
    
    function stats:set_score(score)
        self.final_score = score
    end
    
    function stats:set_level(level)
        self.level = level
    end
    
    function stats:set_accuracy(accuracy)
        self.accuracy = accuracy
    end
    
    function stats:add_kill()
        self.kills = self.kills + 1
    end
    
    function stats:add_death()
        self.deaths = self.deaths + 1
    end
    
    function stats:add_shot_fired()
        self.shots_fired = self.shots_fired + 1
    end
    
    function stats:add_shot_hit()
        self.shots_hit = self.shots_hit + 1
    end
    
    function stats:set_custom_metric(key, value)
        self.custom_metrics[key] = value
    end
    
    function stats:finalize()
        self.duration = os.time() - self.start_time
        self.time_elapsed = self.duration
        
        -- Calculate final accuracy if we have shot data
        if self.shots_fired > 0 then
            self.accuracy = math.floor((self.shots_hit / self.shots_fired) * 100)
        end
        
        return self
    end
    
    function stats:get_summary()
        return {
            game = self.game_name,
            score = self.final_score,
            level = self.level,
            duration = self.duration,
            accuracy = self.accuracy,
            kills = self.kills,
            deaths = self.deaths,
            shots_fired = self.shots_fired,
            shots_hit = self.shots_hit,
            custom = self.custom_metrics
        }
    end
    
    return stats
end

return game_stats
