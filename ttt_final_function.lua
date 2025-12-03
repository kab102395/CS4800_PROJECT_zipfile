-- Add this to the end of ttt.gui_script

function final(self)
    -- Stop receiving projection info when this GUI unloads
    msg.post("main:/proxy_relay#script", "unregister_projection_client")
end
