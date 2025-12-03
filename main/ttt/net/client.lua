local websocket = require "main.ttt.net.ws"   -- your shim that returns the native module

local M = { url = nil, http_url = "http://127.0.0.1:8081" }
local ws = nil
local queue = {}
local pending_messages = {}  -- messages to send once connected
local connection_state = "disconnected"  -- track connection state manually
local session_id = nil  -- our session ID for HTTP requests

local function cb(self, conn, data)
  print("WS callback:", data.event, data.code, data.message)
  if data.event == websocket.EVENT_CONNECTED then
    print("WS connected")
    connection_state = "connected"
    -- send any pending messages
    for _, msg in ipairs(pending_messages) do
      M.send(msg)
    end
    pending_messages = {}
  elseif data.event == websocket.EVENT_DISCONNECTED then
    print("WS closed", data.code, data.message or "")
    connection_state = "disconnected"
    ws = nil
    -- Clear any pending messages since we disconnected
    pending_messages = {}
    
  elseif data.event == websocket.EVENT_MESSAGE then
    print("Raw message received:", data.message)
    local ok, obj = pcall(function() 
      -- Enhanced JSON parsing with better error handling
      if json and json.decode then
        return json.decode(data.message)
      elseif _G.json and _G.json.decode then
        return _G.json.decode(data.message)
      else
        -- Enhanced manual JSON parsing for our message types
        local t_start = string.find(data.message, '"t":"')
        if t_start then
          local t_end = string.find(data.message, '"', t_start + 4)
          if t_end then
            local msg_type = string.sub(data.message, t_start + 4, t_end - 1)
            local parsed = {t = msg_type, raw = data.message}
            
            -- Enhanced field extraction function
            local function extract_field(field_name)
              local pattern = '"' .. field_name .. '":"([^"]*)"'
              local value = string.match(data.message, pattern)
              if value then
                return value
              end
              -- Try boolean pattern
              local bool_pattern = '"' .. field_name .. '":(%a+)'
              local bool_value = string.match(data.message, bool_pattern)
              if bool_value == "true" then
                return true
              elseif bool_value == "false" then
                return false
              end
              return nil
            end
            
            -- Parse all important fields
            parsed.sessionId = extract_field("sessionId")
            parsed.matchId = extract_field("matchId")
            parsed.yourMark = extract_field("yourMark")
            parsed.yourName = extract_field("yourName")
            parsed.opponentName = extract_field("opponentName")
            parsed.board = extract_field("board")
            parsed.message = extract_field("message")
            parsed.yourTurn = extract_field("yourTurn")
            parsed.serverVersion = extract_field("serverVersion")
            
            print("Enhanced parsing - Type:", parsed.t, "MatchId:", parsed.matchId, "YourMark:", parsed.yourMark, "YourTurn:", parsed.yourTurn)
            return parsed
          end
        end
        error("No JSON parser available and manual parsing failed")
      end
    end)
    if ok then 
      print("Parsed message:", obj.t)
      queue[#queue + 1] = obj 
    else 
      print("WS bad JSON:", data.message, "Error:", obj) 
    end
  elseif data.event == websocket.EVENT_ERROR then
    print("WS error", data.message or "")
    connection_state = "error"
  end
end

-- try a call shape; if it errors, return nil
local function try_connect(...)
  local ok, sock_or_err = pcall(websocket.connect, ...)
  if ok then 
    print("WebSocket connect pcall succeeded")
    return sock_or_err 
  end
  print("WebSocket connect failed:", sock_or_err)
  return nil, sock_or_err
end

function M.connect(url)
  M.url = url or M.url or "ws://127.0.0.1:8080"
  print("Attempting to connect to:", M.url)
  connection_state = "connecting"

  -- already open?
  if ws and connection_state == "connected" then 
    print("Already connected")
    return 
  end

  -- try the correct API: websocket.connect(url, callback)
  print("Trying connect with callback...")
  ws = try_connect(M.url, cb)
  if not ws then
    -- try with empty protocols table
    print("Trying connect with protocols table...")
    ws = try_connect(M.url, {}, cb)
  end
  if not ws then
    -- try with all parameters
    print("Trying connect with all parameters...")
    ws = try_connect(M.url, {}, {}, 10, cb)
  end
  
  if ws then
    print("Connect call succeeded, waiting for callback...")
  else
    print("All connect attempts failed")
    connection_state = "failed"
  end
end

function M.send(tbl)
  if connection_state ~= "connected" then 
    print("Not connected (state:", connection_state .. "), queueing message")
    table.insert(pending_messages, tbl)
    return 
  end
  
  -- Try using Defold's built-in JSON encoding
  local s
  if type(_G) == "table" and _G.json and _G.json.encode then
    s = _G.json.encode(tbl)
  else
    -- Fallback: Simple JSON encoding for our specific messages
    s = "{"
    local first = true
    for k, v in pairs(tbl) do
      if not first then s = s .. "," end
      s = s .. '"' .. tostring(k) .. '":"' .. tostring(v) .. '"'
      first = false
    end
    s = s .. "}"
  end
  
  print("Sending message:", s)
  print("Message length:", string.len(s))
  print("WebSocket state check before send...")
  print("WebSocket object:", ws)
  print("WebSocket type:", type(ws))
  
  -- Send the message using the correct Defold WebSocket API
  local success = false
  
  -- Defold WebSocket extension uses websocket.send(connection, message)
  if websocket and websocket.send and ws then
    print("Attempting websocket.send with connection:", ws)
    local result = websocket.send(ws, s)
    print("Send result:", result)
    
    -- Check for successful send - different extensions may have different success values
    if result == 0 or result == websocket.RESULT_OK or result == true then
      print("WebSocket send successful")
      success = true
    else
      print("WebSocket send failed, result code:", result)
      
      -- Try alternative: check if result is nil/falsy
      if result == nil then
        print("Send returned nil - may still be successful")
        success = true
      end
    end
  else
    print("WebSocket send failed - missing websocket module or connection")
    print("websocket module exists:", websocket ~= nil)
    print("websocket.send exists:", websocket and websocket.send ~= nil)
    print("connection exists:", ws ~= nil)
  end
  
  if not success then
    print("All send methods failed")
  end
end

function M.poll()
  return function()
    if #queue == 0 then return nil end
    return table.remove(queue, 1)
  end
end

function M.close()
  if ws then websocket.close(ws) end
  ws = nil
end

function M.connected()
  return connection_state == "connected"
end

-- HTTP functions for game actions
function M.join_game(callback)
  if not session_id then
    -- Generate truly unique session ID using multiple entropy sources
    math.randomseed(os.time() * 1000 + os.clock() * 1000000)
    local time_part = tostring(os.time())
    local clock_part = tostring(math.floor(os.clock() * 1000000))
    local random_part = tostring(math.random(100000, 999999))
    local extra_random = tostring(math.random(10000, 99999))
    session_id = "defold-" .. time_part .. "-" .. clock_part .. "-" .. random_part .. "-" .. extra_random
    print("Generated unique session ID:", session_id)
  end
  
  local request_body = '{"sessionId":"' .. session_id .. '"}'
  
  http.request(M.http_url .. "/api/join", "POST", function(self, id, response)
    print("Join game HTTP response:", response.status, response.response)
    print("Generated session ID for this request:", session_id)
    if callback then
      if response.status == 200 then
        -- Parse the JSON response manually for now
        local match_id_start = string.find(response.response, '"matchId":"')
        if match_id_start then
          local match_id_end = string.find(response.response, '"', match_id_start + 11)
          if match_id_end then
            local match_id = string.sub(response.response, match_id_start + 11, match_id_end - 1)
            print("Parsed match_id:", match_id, "for session:", session_id)
            callback(true, match_id, session_id)
            return
          end
        end
      end
      callback(false, nil, nil)
    end
  end, {["Content-Type"] = "application/json"}, request_body)
end

function M.make_move(match_id, cell, callback)
  if not session_id or not match_id then
    if callback then callback(false) end
    return
  end
  
  -- Server expects 'cell' parameter
  local request_body = '{"sessionId":"' .. session_id .. '","matchId":"' .. match_id .. '","cell":' .. tostring(cell) .. '}'
  
  http.request(M.http_url .. "/api/move", "POST", function(self, id, response)
    print("Make move HTTP response:", response.status, response.response)
    if callback then
      if response.status == 200 then
        -- Enhanced response parsing for new server format
        local success = false
        if response.response then
          if string.find(response.response, '"success":true') then
            success = true
          elseif string.find(response.response, '"moveValid":true') then
            success = true
          end
        end
        callback(success)
      else
        callback(false)
      end
    end
  end, {["Content-Type"] = "application/json"}, request_body)
end

function M.get_session_id()
  return session_id
end

-- NEW: Get available matches
function M.get_available_matches(callback)
  http.request(M.http_url .. "/api/matches", "GET", function(self, id, response)
    print("Get matches HTTP response:", response.status, response.response)
    if callback then
      if response.status == 200 then
        -- Parse JSON response for matches list
        callback(true, response.response)
      else
        callback(false, nil)
      end
    end
  end, {["Content-Type"] = "application/json"})
end

-- NEW: Create a new match
function M.create_match(player_name, match_name, callback)
  if not session_id then
    -- Generate session ID if not exists
    math.randomseed(os.time() * 1000 + os.clock() * 1000000)
    local time_part = tostring(os.time())
    local clock_part = tostring(math.floor(os.clock() * 1000000))
    local random_part = tostring(math.random(100000, 999999))
    local extra_random = tostring(math.random(10000, 99999))
    session_id = "defold-" .. time_part .. "-" .. clock_part .. "-" .. random_part .. "-" .. extra_random
  end
  
  -- Build match name
  local final_match_name = match_name or (player_name .. "'s Game")
  
  local request_body = '{"sessionId":"' .. session_id .. '","playerName":"' .. player_name .. '","matchName":"' .. final_match_name .. '"}'
  
  http.request(M.http_url .. "/api/create-match", "POST", function(self, id, response)
    print("Create match HTTP response:", response.status, response.response)
    if callback then
      if response.status == 200 then
        -- Parse JSON to get match ID
        local match_id_start = string.find(response.response, '"matchId":"')
        if match_id_start then
          local match_id_end = string.find(response.response, '"', match_id_start + 11)
          if match_id_end then
            local match_id = string.sub(response.response, match_id_start + 11, match_id_end - 1)
            callback(true, match_id, session_id)
            return
          end
        end
      end
      callback(false, nil, nil)
    end
  end, {["Content-Type"] = "application/json"}, request_body)
end

-- NEW: Join a specific match
function M.join_specific_match(player_name, match_id, callback)
  if not session_id then
    -- Generate session ID if not exists
    math.randomseed(os.time() * 1000 + os.clock() * 1000000)
    local time_part = tostring(os.time())
    local clock_part = tostring(math.floor(os.clock() * 1000000))
    local random_part = tostring(math.random(100000, 999999))
    local extra_random = tostring(math.random(10000, 99999))
    session_id = "defold-" .. time_part .. "-" .. clock_part .. "-" .. random_part .. "-" .. extra_random
  end
  
  local request_body = '{"sessionId":"' .. session_id .. '","playerName":"' .. player_name .. '","matchId":"' .. match_id .. '"}'
  
  http.request(M.http_url .. "/api/join-match", "POST", function(self, id, response)
    print("Join specific match HTTP response:", response.status, response.response)
    if callback then
      if response.status == 200 then
        -- Check if join was successful
        if string.find(response.response, '"success":true') then
          callback(true, match_id, session_id)
        else
          callback(false, nil, nil)
        end
      else
        callback(false, nil, nil)
      end
    end
  end, {["Content-Type"] = "application/json"}, request_body)
end

-- NEW: Poll for game state updates (replaces WebSocket notifications)
function M.poll_game_state(callback)
  if not session_id then
    print("No session ID for polling")
    if callback then callback(false, nil) end
    return
  end
  
  print("Polling game state for session:", session_id)
  
  http.request(M.http_url .. "/api/game-state/" .. session_id, "GET", function(self, id, response)
    print("Game state poll response:", response.status, response.response)
    if callback then
      if response.status == 200 then
        -- Parse the game state response
        local ok, game_state = pcall(function()
          if json and json.decode then
            return json.decode(response.response)
          elseif _G.json and _G.json.decode then
            return _G.json.decode(response.response)
          else
            -- Simple manual parsing for basic success check
            if string.find(response.response, '"success":true') then
              return {success = true, raw = response.response}
            else
              return {success = false}
            end
          end
        end)
        
        if ok and game_state and game_state.success then
          callback(true, game_state)
        else
          print("Failed to parse game state:", game_state)
          callback(false, nil)
        end
      else
        callback(false, nil)
      end
    end
  end)
end

-- Get player statistics
function M.get_player_stats(player_name, callback)
  print("=== CLIENT: Getting player stats for:", player_name)
  
  http.request(M.http_url .. "/api/stats/" .. player_name, "GET", function(self, id, response)
    print("Stats response code:", response.status)
    print("Stats response body:", response.response)
    
    if response.status == 200 then
      local ok, stats = pcall(function()
        if json and json.decode then
          return json.decode(response.response)
        else
          -- Manual parsing for stats response
          local stats = {success = false}
          
          if string.find(response.response, '"success":true') then
            stats.success = true
            
            -- Extract player name
            local name_match = string.match(response.response, '"playerName":"([^"]*)"')
            if name_match then
              stats.playerName = name_match
            end
            
            -- Extract found status
            local found_match = string.match(response.response, '"found":([^,}]*)')
            if found_match then
              stats.found = found_match == "true"
            end
            
            -- Extract numeric stats
            local total_match = string.match(response.response, '"totalGames":([^,}]*)')
            if total_match then
              stats.totalGames = tonumber(total_match) or 0
            end
            
            local wins_match = string.match(response.response, '"wins":([^,}]*)')
            if wins_match then
              stats.wins = tonumber(wins_match) or 0
            end
            
            local losses_match = string.match(response.response, '"losses":([^,}]*)')
            if losses_match then
              stats.losses = tonumber(losses_match) or 0
            end
            
            local draws_match = string.match(response.response, '"draws":([^,}]*)')
            if draws_match then
              stats.draws = tonumber(draws_match) or 0
            end
            
            local winrate_match = string.match(response.response, '"winRate":([^,}]*)')
            if winrate_match then
              stats.winRate = tonumber(winrate_match) or 0.0
            end
            
            -- Extract message if present
            local message_match = string.match(response.response, '"message":"([^"]*)"')
            if message_match then
              stats.message = message_match
            end
          end
          
          return stats
        end
      end)
      
      if ok and stats and stats.success then
        callback(true, stats)
      else
        print("Failed to parse stats response:", stats)
        callback(false, nil)
      end
    else
      callback(false, nil)
    end
  end)
end

return M
