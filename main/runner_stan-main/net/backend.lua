local json = require "json"
local auth = require("main.scripts.auth")

local M = {}

local function ensure_username()
	local session = auth.load_session()
	return session and session.username or nil
end

-- Submit run using unified score endpoint (gameName=runner, level=coins)
function M.post_run(score, coins, callback)
	local username = ensure_username()
	if not username then
		if callback then callback(false, { error = "No username" }) end
		return
	end
	coins = coins or 0
	auth.submit_score(username, "runner", score, coins, function(success, resp)
		if callback then callback(success, resp) end
	end)
end

-- Fetch leaderboard using unified endpoint, map to runs list
function M.fetch_runs(callback)
	auth.get_leaderboard("runner", 10, function(success, data)
		if not success or not data then
			if callback then callback(false, nil) end
			return
		end
		-- data is already decoded JSON array: { username, score, timestamp }
		local runs = {}
		for _, entry in ipairs(data) do
			table.insert(runs, {
				player = entry.username or "unknown",
				score = entry.score or 0,
				coins = entry.level or entry.duration or 0
			})
		end
		if callback then callback(true, { runs = runs }) end
	end)
end

return M
