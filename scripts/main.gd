extends Node2D

var player := Vector2(640, 360)
var hp := 100.0
var ammo := 30
var reserve := 120
var score := 0
var wave := 1
var zombies: Array[Dictionary] = []
var bullets: Array[Dictionary] = []
var spawn_left := 6
var spawn_timer := 0.0
var shot_timer := 0.0
var reload_timer := 0.0
var game_over := false
var won := false
var move_touch := -1
var aim_touch := -1
var move_origin := Vector2.ZERO
var move_pos := Vector2.ZERO
var aim_origin := Vector2.ZERO
var aim_pos := Vector2.ZERO
var aim_dir := Vector2.RIGHT
var fire_held := false
var rng := RandomNumberGenerator.new()

func _ready() -> void:
	rng.randomize()
	set_process(true)
	queue_redraw()

func _process(delta: float) -> void:
	if game_over:
		queue_redraw()
		return
	shot_timer = maxf(0.0, shot_timer - delta)
	if reload_timer > 0:
		reload_timer -= delta
		if reload_timer <= 0:
			var load_amount := mini(30 - ammo, reserve)
			ammo += load_amount
			reserve -= load_amount

	var keyboard := Input.get_vector("move_left", "move_right", "move_up", "move_down")
	var movement := keyboard
	if move_touch >= 0:
		movement = (move_pos - move_origin).limit_length(75.0) / 75.0
	player += movement * 245.0 * delta
	player.x = clampf(player.x, 35.0, 1245.0)
	player.y = clampf(player.y, 70.0, 685.0)

	if aim_touch >= 0 and aim_pos.distance_to(aim_origin) > 12:
		aim_dir = (aim_pos - aim_origin).normalized()
		fire_held = true
	if Input.is_mouse_button_pressed(MOUSE_BUTTON_LEFT):
		aim_dir = (get_global_mouse_position() - player).normalized()
		fire_held = true
	if fire_held and shot_timer <= 0 and reload_timer <= 0:
		shoot()

	spawn_timer -= delta
	if spawn_left > 0 and spawn_timer <= 0:
		spawn_zombie()
		spawn_left -= 1
		spawn_timer = 0.55
	elif spawn_left == 0 and zombies.is_empty():
		wave += 1
		spawn_left = 4 + wave * 2
		spawn_timer = 2.5

	for z in zombies:
		var d: Vector2 = player - z.pos
		if d.length() > 28:
			z.pos += d.normalized() * z.speed * delta
		else:
			z.attack -= delta
			if z.attack <= 0:
				hp -= 9.0
				z.attack = 0.75
				if hp <= 0:
					game_over = true

	for b in bullets:
		b.pos += b.dir * 780.0 * delta
		b.life -= delta
	for bi in range(bullets.size() - 1, -1, -1):
		var b: Dictionary = bullets[bi]
		var hit := false
		for zi in range(zombies.size() - 1, -1, -1):
			if b.pos.distance_to(zombies[zi].pos) < 24:
				zombies[zi].hp -= 25
				hit = true
				if zombies[zi].hp <= 0:
					zombies.remove_at(zi)
					score += 1
				break
		if hit or b.life <= 0:
			bullets.remove_at(bi)

	if player.x > 1130 and player.y < 155 and score >= 10:
		won = true
	queue_redraw()

func shoot() -> void:
	if ammo <= 0:
		reload()
		return
	ammo -= 1
	shot_timer = 0.12
	bullets.append({"pos": player + aim_dir * 25.0, "dir": aim_dir, "life": 1.3})

func reload() -> void:
	if reload_timer <= 0 and ammo < 30 and reserve > 0:
		reload_timer = 1.25

func spawn_zombie() -> void:
	var edge := rng.randi_range(0, 3)
	var p := Vector2.ZERO
	if edge == 0: p = Vector2(rng.randf_range(20, 1260), 45)
	elif edge == 1: p = Vector2(1260, rng.randf_range(55, 700))
	elif edge == 2: p = Vector2(rng.randf_range(20, 1260), 700)
	else: p = Vector2(20, rng.randf_range(55, 700))
	zombies.append({"pos": p, "hp": 50.0 + wave * 4.0, "speed": 75.0 + wave * 3.0, "attack": 0.0})

func reset() -> void:
	player = Vector2(640, 360); hp = 100; ammo = 30; reserve = 120
	score = 0; wave = 1; zombies.clear(); bullets.clear(); spawn_left = 6
	game_over = false; won = false; reload_timer = 0; queue_redraw()

func _input(event: InputEvent) -> void:
	if event is InputEventScreenTouch:
		if event.pressed:
			if game_over:
				reset(); return
			if event.position.x < 430 and move_touch < 0:
				move_touch = event.index; move_origin = event.position; move_pos = event.position
			elif event.position.x > 850 and aim_touch < 0:
				aim_touch = event.index; aim_origin = event.position; aim_pos = event.position
		else:
			if event.index == move_touch: move_touch = -1
			if event.index == aim_touch: aim_touch = -1; fire_held = false
	elif event is InputEventScreenDrag:
		if event.index == move_touch: move_pos = event.position
		if event.index == aim_touch: aim_pos = event.position
	elif event is InputEventKey and event.pressed and event.keycode == KEY_R:
		reload()

func _draw() -> void:
	# Arena
	draw_rect(Rect2(0, 0, 1280, 720), Color("101820"))
	for x in range(0, 1280, 80): draw_line(Vector2(x, 55), Vector2(x, 720), Color("182631"), 2)
	for y in range(55, 720, 80): draw_line(Vector2(0, y), Vector2(1280, y), Color("182631"), 2)
	draw_rect(Rect2(1110, 55, 145, 110), Color("174f32"), true)
	draw_string(ThemeDB.fallback_font, Vector2(1130, 115), "SAFE ROOM", HORIZONTAL_ALIGNMENT_LEFT, -1, 18, Color("7dff9b"))
	# Player and weapon direction
	draw_circle(player, 20, Color("56b4ff")); draw_line(player, player + aim_dir * 38, Color.WHITE, 7)
	for z in zombies:
		draw_circle(z.pos, 22, Color("62b44b")); draw_circle(z.pos + Vector2(-7,-5), 3, Color("ff382e")); draw_circle(z.pos + Vector2(7,-5), 3, Color("ff382e"))
	for b in bullets: draw_circle(b.pos, 5, Color("ffe56b"))
	# HUD
	draw_rect(Rect2(0,0,1280,55), Color(0,0,0,0.82), true)
	var status := "HP %d     AMMO %d/%d     KILLS %d     WAVE %d" % [maxi(0,int(hp)), ammo, reserve, score, wave]
	if reload_timer > 0: status += "     RELOADING..."
	draw_string(ThemeDB.fallback_font, Vector2(22,36), status, HORIZONTAL_ALIGNMENT_LEFT, -1, 23, Color.WHITE)
	var goal := "SURVIVE • GET 10 KILLS • REACH SAFE ROOM"
	draw_string(ThemeDB.fallback_font, Vector2(760,35), goal, HORIZONTAL_ALIGNMENT_LEFT, -1, 17, Color("d2e4ee"))
	# Touch controls
	var left_c := move_origin if move_touch >= 0 else Vector2(130,590)
	var right_c := aim_origin if aim_touch >= 0 else Vector2(1140,590)
	draw_circle(left_c, 76, Color(1,1,1,.10)); draw_circle(left_c if move_touch < 0 else move_pos.limit_length(9999), 34, Color(1,1,1,.26))
	draw_circle(right_c, 76, Color(1,.15,.12,.13)); draw_circle(right_c if aim_touch < 0 else aim_pos, 34, Color(1,.2,.15,.35))
	draw_string(ThemeDB.fallback_font, left_c + Vector2(-30,6), "MOVE", HORIZONTAL_ALIGNMENT_LEFT, -1, 15, Color.WHITE)
	draw_string(ThemeDB.fallback_font, right_c + Vector2(-28,6), "FIRE", HORIZONTAL_ALIGNMENT_LEFT, -1, 15, Color.WHITE)
	if won:
		draw_rect(Rect2(260,245,760,200),Color(0,.16,.08,.92),true)
		draw_string(ThemeDB.fallback_font,Vector2(430,350),"SAFE ROOM REACHED — YOU SURVIVED!",HORIZONTAL_ALIGNMENT_LEFT,-1,28,Color("7dff9b"))
	if game_over:
		draw_rect(Rect2(0,0,1280,720),Color(0,0,0,.82),true)
		draw_string(ThemeDB.fallback_font,Vector2(470,320),"YOU WERE OVERRUN",HORIZONTAL_ALIGNMENT_LEFT,-1,38,Color("ff5147"))
		draw_string(ThemeDB.fallback_font,Vector2(490,380),"Tap anywhere to restart",HORIZONTAL_ALIGNMENT_LEFT,-1,23,Color.WHITE)
