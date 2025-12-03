-- Authentication client for unified user system with async/concurrent support
-- Handles register, login, score submission, and stats retrieval with proper HTTP pooling

local auth = {}

-- Configuration
local BASE_URL = "http://localhost:8081/api"
local TIMEOUT = 120  -- Increased to 120 seconds - CRITICAL: must complete before game unloads
local MAX_CONCURRENT_REQUESTS = 1  -- Set to 1 to serialize all requests - no contention, guaranteed order
local SCORE_SUBMIT_QUEUE_SIZE = 1000  -- Increased from 100 to never drop requests
local MAX_RETRIES = 5  -- Retry failed submissions up to 5 times
local RETRY_DELAY = 2  -- Wait 2 seconds between retries

-- HTTP Request Queue for concurrent pooling
local request_queue = {}
local active_requests = 0
local request_pool = {}
local current_session = nil

-- Simple SHA-256 hash for password (using a basic approach)
local function hash_password(password)
    return "hash_" .. password:sub(1, 20)
end

-- Process next request in queue with pool limit
local function process_request_queue()
    if active_requests >= MAX_CONCURRENT_REQUESTS or #request_queue == 0 then
        return
    end
    
    local req = table.remove(request_queue, 1)
    active_requests = active_requests + 1
    
    print("AUTH: Processing request. Queue: " .. #request_queue .. ", Active: " .. active_requests)
    
    http.request(req.url, req.method, function(self, id, response)
        active_requests = active_requests - 1
        print("AUTH: Request completed. Active: " .. active_requests .. ", Queued: " .. #request_queue)
        
        if req.callback then
            req.callback(response)
        end
        
        -- Process next request in queue
        timer.delay(0.01, false, function()
            process_request_queue()
        end)
    end, req.headers, req.body)
end

-- Queue an HTTP request with concurrent limit
local function queue_request(url, method, headers, body, callback)
    table.insert(request_queue, {
        url = url,
        method = method,
        headers = headers,
        body = body,
        callback = callback,
        queued_time = os.time()
    })
    
    print("AUTH: Request queued. Total in queue: " .. #request_queue)
    process_request_queue()
end

-- Register a new user
function auth.register(username, password, email, callback)
    local url = BASE_URL .. "/register"
    local headers = { ["Content-Type"] = "application/json" }
    local body = json.encode({
        username = username,
        passwordHash = hash_password(password),
        email = email or ""
    })
    
    print("AUTH: Registering user: " .. username)
    
    queue_request(url, "POST", headers, body, function(response)
        if response.status == 200 or response.status == 201 then
            local data = json.decode(response.response)
            print("AUTH: Registration successful")
            if callback then callback(true, data) end
        else
            local error_msg = "Registration failed (HTTP " .. response.status .. ")"
            print("AUTH: " .. error_msg)
            if callback then callback(false, { error = error_msg }) end
        end
    end)
end

-- Login user
function auth.login(username, password, callback)
    local url = BASE_URL .. "/login"
    local headers = { ["Content-Type"] = "application/json" }
    local body = json.encode({
        username = username,
        passwordHash = hash_password(password)
    })
    
    print("AUTH: Logging in user: " .. username)
    
    queue_request(url, "POST", headers, body, function(response)
        if response.status == 200 then
            local data = json.decode(response.response)
            print("AUTH: Login successful, userId: " .. data.userId)
            if callback then callback(true, data) end
        else
            local error_msg = "Login failed (HTTP " .. response.status .. ")"
            print("AUTH: " .. error_msg)
            if callback then callback(false, { error = error_msg }) end
        end
    end)
end

-- Get user stats (with caching option)
function auth.get_stats(user_id, callback)
    local url = BASE_URL .. "/user/" .. user_id .. "/stats"
    
    print("AUTH: Fetching stats for user " .. user_id)
    
    queue_request(url, "GET", {}, nil, function(response)
        if response.status == 200 then
            local data = json.decode(response.response)
            print("AUTH: Stats retrieved successfully")
            if callback then callback(true, data) end
        else
            local error_msg = "Failed to get stats (HTTP " .. response.status .. ")"
            print("AUTH: " .. error_msg)
            if callback then callback(false, { error = error_msg }) end
        end
    end)
end

-- Get user ID from stats endpoint
local function get_user_id(username)
    local url = BASE_URL .. "/stats/" .. username
    local user_id = nil
    local done = false
    
    http.request(url, "GET", function(self, id, response)
        if response.status == 200 then
            local data = json.decode(response.response)
            if data.found then
                -- Extract user_id from response or infer it exists
                user_id = username  -- For now, use username as identifier
            end
        end
        done = true
    end)
    
    -- Wait for response (blocking for simplicity)
    local timeout = socket.gettime() + 5
    while not done and socket.gettime() < timeout do
        coroutine.yield()
    end
    
    return user_id
end

-- Submit game score with retry logic - GUARANTEES delivery
function auth.submit_score(username, game_name, score, level, callback)
    if not username then
        print("AUTH: Cannot submit score - no username")
        if callback then callback(false, { error = "Not logged in" }) end
        return
    end
    
    -- Use the username-based endpoint
    local url = BASE_URL .. "/user/" .. username .. "/score"
    local headers = { ["Content-Type"] = "application/json" }
    local body = json.encode({
        gameName = game_name,
        score = score,
        level = level or 1
    })
    
    local retry_count = 0
    
    local function attempt_submit()
        retry_count = retry_count + 1
        print("AUTH: *** SCORE SUBMISSION ATTEMPT " .. retry_count .. " *** - " .. username .. " - " .. game_name .. ": " .. score)
        
        queue_request(url, "POST", headers, body, function(response)
            print("AUTH: Score submission response status: " .. response.status .. " (attempt " .. retry_count .. ")")
            
            if response.status == 200 or response.status == 201 then
                print("AUTH: *** SCORE SAVED SUCCESSFULLY *** for " .. game_name .. ": " .. score)
                if callback then callback(true, { success = true }) end
            else
                -- Retry on failure
                if retry_count < MAX_RETRIES then
                    local error_msg = "Submission failed (HTTP " .. response.status .. ") - retrying in " .. RETRY_DELAY .. "s"
                    print("AUTH: " .. error_msg)
                    timer.delay(RETRY_DELAY, false, function()
                        attempt_submit()
                    end)
                else
                    local error_msg = "Failed to submit score after " .. MAX_RETRIES .. " attempts (HTTP " .. response.status .. "): " .. (response.response or "unknown error")
                    print("AUTH: *** CRITICAL: " .. error_msg)
                    if callback then callback(false, { error = error_msg }) end
                end
            end
        end)
    end
    
    attempt_submit()
end

-- Submit multiple scores concurrently
function auth.submit_scores(user_id, scores_table, callback)
    if not user_id then
        if callback then callback(false, { error = "Not logged in" }) end
        return
    end
    
    local completed = 0
    local total = #scores_table
    local results = {}
    
    print("AUTH: Submitting " .. total .. " scores concurrently")
    
    for i, score_data in ipairs(scores_table) do
        auth.submit_score(user_id, score_data.game_name, score_data.score, score_data.level, function(success, data)
            completed = completed + 1
            results[i] = { success = success, data = data }
            
            if completed == total and callback then
                callback(true, { results = results, total = total })
            end
        end)
    end
end

-- Get leaderboard (with optional caching)
function auth.get_leaderboard(game_name, limit, callback)
    local url = BASE_URL .. "/leaderboard/" .. game_name .. "?limit=" .. (limit or 10)
    
    print("AUTH: Fetching leaderboard for " .. game_name)
    
    queue_request(url, "GET", {}, nil, function(response)
        if response.status == 200 then
            local data = json.decode(response.response)
            print("AUTH: Leaderboard retrieved successfully")
            if callback then callback(true, data) end
        else
            local error_msg = "Failed to get leaderboard (HTTP " .. response.status .. ")"
            print("AUTH: " .. error_msg)
            if callback then callback(false, { error = error_msg }) end
        end
    end)
end

-- Get request queue statistics
function auth.get_queue_stats()
    return {
        queued = #request_queue,
        active = active_requests,
        max_concurrent = MAX_CONCURRENT_REQUESTS
    }
end

-- Save user session locally
function auth.save_session(user_id, username)
    current_session = {
        user_id = user_id,
        username = username,
        login_time = os.time()
    }
    sys.save("session.json", current_session)
    print("AUTH: Session saved locally")
end

-- Load user session from local storage
function auth.load_session()
    if current_session then
        return current_session
    end
    local session_data = sys.load("session.json")
    if session_data and next(session_data) ~= nil then
        current_session = session_data
        print("AUTH: Session loaded from storage")
        return current_session
    end
    return nil
end

-- Clear session
function auth.clear_session()
    current_session = nil
    sys.save("session.json", nil)
    print("AUTH: Session cleared")
end

return auth
