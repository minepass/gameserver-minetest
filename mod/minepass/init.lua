-- MinePass Plugin
-- http://minepass.net

local worldpath = minetest.get_worldpath()
local modpath = minetest.get_modpath(minetest.get_current_modname())
local operator = minetest.setting_get("name")
local stepcount = 0

minepass = {
	version = "0.2.1",
	modpath = modpath,
	players = {},
	command_sender = "SERVER",
	command_filename = "/command.txt",
	shadow_auth_filename = "/auth.shadow.txt",
	wrapper_connected = false,
	wrapper_vars = {
		join_url = "http://minepass.net"
	}
}

function minepass:load_players()
	local players = {}

	local file, err = io.open(worldpath .. self.shadow_auth_filename, "r")
	if err then
		minetest.log("error", "Failed to load shadow auth fail.")
		return
	end

	for line in file:lines() do
		local name = string.match(line, "^([^:]+):.*")
		if name then
			players[name] = true
		end
	end
	file:close()

	self.players = players
end

function minepass:run_commands()
	local file, err = io.open(worldpath .. self.command_filename, "r")
	if err then return end

	local line = file:read("*line"); file:close()
	os.remove(worldpath .. self.command_filename)
	if not line then return end

	local type, cmd, args = string.match(line, "^([/#])([^ ]+) *(.*)$")
  if not cmd then return end
	if not args then args = "" end

  if type == "/" then
		if minetest.chatcommands[cmd] then
			minetest.chatcommands[cmd].func(self.command_sender, args)
			if cmd == "auth_reload" then
				self:load_players()
			end
			minetest.log("action", "/" .. cmd .. " " .. args)
		else
			minetest.log("error", "Unknown command: " .. cmd)
		end
	elseif type == "#" then
		if not self.wrapper_connected then
			self.wrapper_connected = true
			minetest.log("action", "MinePass wrapper connected.")
		end
		self.wrapper_vars[cmd] = args
		minetest.log("action", "MP " .. cmd .. " = " .. args)
		if (cmd == "founder_name" and operator and args ~= operator) then
			minetest.log("error", "MinePass founder does not equal server operator:")
			minetest.log("error", args .. " <> " .. operator)
			minetest.log("error", "Please update 'name' setting in minetest.config")
			minetest.request_shutdown()
		end
	end
end


--
-- Global Step

function minepass:step(dtime)
	if stepcount == 5 then
		self:load_players()
		minetest.log("action", "MinePass plugin v" .. self.version)
		minetest.log("action", minetest.get_server_status())
		local modnames = minetest.get_modnames()
		for i, name in ipairs(modnames) do
			minetest.log("action", "[Mod] " .. name)
		end
		minetest.log("action", "End Mod List")
		minetest.log("action", "MinePass plugin loaded.")
		minetest.log("action", "awaiting connection from MP wrapper...")
	end

	if stepcount > 5 then
		self:run_commands()
	end

	if not self.wrapper_connected then
		if stepcount == 80 then
			minetest.log("error", "MP wrapper still not connected")
		end
		if stepcount == 200 then
			minetest.log("error", "MinePass failed. Shutting down...")
			minetest.request_shutdown()
		end
	end

	stepcount = stepcount + 1
end

minetest.register_globalstep(function(dtime)
	return minepass:step(dtime)
end)


--
-- Game Hooks

minetest.register_on_prejoinplayer(function(name, ip)
	if minepass.players[name] then
		return
	end

	return "You do not have a MinePass for this server.\n" .. minepass.wrapper_vars.join_url
end)
