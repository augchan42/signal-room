# Spike (throwaway): play the exported performance in real time and report frame times.
extends Node3D

const SCENE := "res://export/warm-driving-frontman.glb"
const TRACKS := "res://export/warm-driving-frontman.tracks.json"
const FPS := 24.0
const FRAMES := 480
# Blender watts to Godot light energy, chosen by eye against the rendered video.
const AREA_WATTS_PER_ENERGY := 130.0
const SPOT_WATTS_PER_ENERGY := 90.0

var data: Dictionary
var lights := {}        # Blender light name -> Light3D
var materials := {}     # material name -> Array[StandardMaterial3D]
var player: AnimationPlayer
var label: Label
var frame_times: Array[float] = []
var report_at := 1.0
var elapsed := 0.0


func _ready() -> void:
	var stage: Node3D = load(SCENE).instantiate()
	add_child(stage)
	data = JSON.parse_string(FileAccess.get_file_as_string(TRACKS))
	var players := stage.find_children("*", "AnimationPlayer", true, false)
	print("PLAYERS ", players.size(), " ", players.map(func(p): return [p.get_path(), p.root_node, p.current_animation, p.autoplay, p.deterministic]))
	player = players[0]
	_merge_animations()
	_use_camera(stage)
	_index_lights_and_materials(stage)
	_add_area_lights()
	_add_environment()
	label = Label.new()
	label.add_theme_font_size_override("font_size", 22)
	var layer := CanvasLayer.new()
	layer.add_child(label)
	add_child(layer)


func _merge_animations() -> void:
	# The exporter wrote one animation per animated object, and the importer padded each with a
	# one-key rest track for every other animated joint. Merge them into one 20-second clip,
	# keeping for each joint and channel only the track that actually moves.
	var best := {}  # "path|type" -> [animation, track index]
	for lib_name in player.get_animation_library_list():
		var lib := player.get_animation_library(lib_name)
		for anim_name in lib.get_animation_list():
			var anim := lib.get_animation(anim_name)
			for t in anim.get_track_count():
				var key := "%s|%d" % [anim.track_get_path(t), anim.track_get_type(t)]
				if not best.has(key) or anim.track_get_key_count(t) > best[key][0].track_get_key_count(best[key][1]):
					best[key] = [anim, t]
	var merged := Animation.new()
	merged.length = FRAMES / FPS
	for key in best:
		best[key][0].copy_track(best[key][1], merged)
	print("MERGED tracks=", merged.get_track_count())
	var lib := AnimationLibrary.new()
	lib.add_animation("show", merged)
	player.add_animation_library("spike", lib)
	if "--no-anim" in OS.get_cmdline_user_args():
		player.stop()
		set_process(false)
		if "--raise-arm" in OS.get_cmdline_user_args():
			for n in find_children("*Cropped jacket shoulder*", "", true, false):
				n.quaternion = Quaternion(Vector3(0, 0, 1), -PI / 2)
				print("RAISED ", n.get_path(), " children=", n.get_children())
		return
	# Drive the clip from a clock, as the app would from the audio position: seeking with an
	# update every frame. Letting the player run on its own stopped updating the picture.
	player.play("spike/show")
	player.pause()
	if "--wave" in OS.get_cmdline_user_args():
		player.stop()
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--freeze="):
			player.seek(float(a.substr(9)), true)
			set_meta("frozen", true)
			print("FROZEN at ", player.current_animation_position)


func _use_camera(stage: Node) -> void:
	for cam in stage.find_children("*", "Camera3D", true, false):
		if "Broadcast group" in cam.name:
			cam.current = true
			print("CAMERA ", cam.name, " size=", cam.size, " proj=", cam.projection, " pos=", cam.global_position)
			return


func _index_lights_and_materials(stage: Node) -> void:
	for light in stage.find_children("*", "Light3D", true, false):
		for blender_name in data.lights:
			if light.name == _godot_name(blender_name):
				lights[blender_name] = light
				(light as SpotLight3D).spot_range = 30.0
	for mesh in stage.find_children("*", "MeshInstance3D", true, false):
		# Blender's volume haze arrives as a solid box; the mobile renderer has no volumetric fog.
		if "haze" in String(mesh.name).to_lower():
			mesh.visible = false
			print("HIDDEN ", mesh.name)
			continue
		var m: Mesh = mesh.mesh
		for i in m.get_surface_count():
			var mat := m.surface_get_material(i) as StandardMaterial3D
			if mat:
				if not materials.has(mat.resource_name):
					materials[mat.resource_name] = []
				if not materials[mat.resource_name].has(mat):
					materials[mat.resource_name].append(mat)


func _godot_name(n: String) -> String:
	return n.validate_node_name()


func _add_area_lights() -> void:
	# glTF has no area lights: stand in a wide soft spot light at the same place and aim.
	var c := Basis(Vector3(1, 0, 0), Vector3(0, 0, -1), Vector3(0, 1, 0))  # Blender Z-up -> Y-up
	for blender_name in data.lights:
		var info: Dictionary = data.lights[blender_name]
		if info.type != "AREA":
			continue
		var m: Array = info.matrix
		var b := Basis(Vector3(m[0][0], m[1][0], m[2][0]), Vector3(m[0][1], m[1][1], m[2][1]), Vector3(m[0][2], m[1][2], m[2][2]))
		var origin := Vector3(m[0][3], m[1][3], m[2][3])
		var spot := SpotLight3D.new()
		spot.name = _godot_name(blender_name)
		spot.transform = Transform3D(c * b, c * origin)  # both lights shine down their own -Z
		spot.spot_angle = 70.0
		spot.spot_angle_attenuation = 0.6
		spot.spot_range = 20.0
		spot.light_size = info.size
		spot.light_color = Color(info.color[0], info.color[1], info.color[2])
		spot.light_energy = info.energy / AREA_WATTS_PER_ENERGY
		spot.shadow_enabled = blender_name.begins_with("Warm portrait")
		add_child(spot)
		lights[blender_name] = spot


func _add_environment() -> void:
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.008, 0.012, 0.022)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.05, 0.06, 0.09)
	env.ambient_light_energy = 0.6
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.glow_enabled = true
	env.glow_intensity = 0.8
	env.glow_bloom = 0.05
	var world := WorldEnvironment.new()
	world.environment = env
	add_child(world)


var clock := 0.0


func _process(delta: float) -> void:
	if "--wave" in OS.get_cmdline_user_args():
		clock += delta
		var sh := find_children("*Cropped jacket shoulder", "", true, false)[0] as Node3D
		sh.quaternion = Quaternion(Vector3(0, 0, 1), -abs(sin(clock)) * PI / 2)
	elif not has_meta("frozen"):
		clock = fmod(clock + delta, FRAMES / FPS)
		player.seek(clock, true)
	var frame := clampi(int(player.current_animation_position * FPS), 0, FRAMES - 1)
	for key in ([] if "--no-tracks" in OS.get_cmdline_user_args() else data.tracks):
		var parts: PackedStringArray = key.split("|")
		var value: float = data.tracks[key][frame]
		var kind := parts[0]
		# Names can contain "|": the owner is everything between the kind and the last two fields.
		var owner := "|".join(parts.slice(1, parts.size() - 2))
		if kind == "light" and lights.has(owner):
			var watts_per := AREA_WATTS_PER_ENERGY if data.lights[owner].type == "AREA" else SPOT_WATTS_PER_ENERGY
			lights[owner].light_energy = value / watts_per
		elif kind == "material":
			for mat in materials.get(owner, []):
				mat.emission_energy_multiplier = value
	if frame in [120, 240] and not has_meta("p%d" % frame):
		set_meta("p%d" % frame, true)
		var hand := find_children("01 Cropped jacket hand", "", true, false)[0] as Node3D
		var sh := find_children("*Cropped jacket shoulder", "", true, false)[0] as Node3D
		for m in find_children("*Cropped jacket*", "MeshInstance3D", true, false):
			if "arm" in String(m.name) or "hand" in String(m.name):
				print("  MESH ", m.name, " visible=", m.is_visible_in_tree(), " gpos=", m.global_position, " parent=", m.get_parent().name)
		print("HAND frame=", frame, " t=", player.current_animation_position, " hand_y=", hand.global_position.y, " shoulder_q=", sh.quaternion)
	if false:
		set_meta("probed", true)
		for n in find_children("*Cropped jacket shoulder*", "", true, false):
			print("PROBE ", n.name, " q=", n.quaternion, " t=", player.current_animation_position, " arm_global_basis_y=", (n.get_child(0) as Node3D).global_basis.y)
		var anim := player.get_animation("spike/show")
		for t in anim.get_track_count():
			if "Cropped jacket shoulder" in String(anim.track_get_path(t)):
				print("PROBETRACK ", anim.track_get_path(t), " keys=", anim.track_get_key_count(t), " v240=", anim.track_get_key_value(t, 240), " interp=", anim.track_get_interpolation_type(t))
	# Frame-time report once a second, to the screen and the log.
	elapsed += delta
	frame_times.append(delta * 1000.0)
	if elapsed >= report_at:
		report_at += 1.0
		var sorted := frame_times.duplicate()
		sorted.sort()
		var p50: float = sorted[sorted.size() / 2]
		var p99: float = sorted[mini(sorted.size() - 1, int(sorted.size() * 0.99))]
		var text := "SPIKE fps=%d p50=%.1fms p99=%.1fms max=%.1fms" % [Engine.get_frames_per_second(), p50, p99, sorted[-1]]
		label.text = text
		print(text)
		frame_times.clear()
