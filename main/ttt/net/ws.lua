-- net/ws.lua - shim to load the native websocket module
local ok, mod = pcall(require, "websocket")  -- this is the native module from the extension
if not ok then
    error("Defold extension-websocket not available. Did you Fetch Libraries?")
end
return mod
