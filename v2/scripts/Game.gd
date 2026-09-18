extends Node3D

var player: CharacterBody3D
var camera: Camera3D
var zombies: Array[CharacterBody3D] = []
var model_scene: PackedScene
var move_touch := -1
var look_touch := -1
var move_origin := Vector2.ZERO
var move_now := Vector2.ZERO
var yaw := 0.0
var pitch := 0.0
var spawn_timer := 0.4
var remaining := 8
var wave := 1
var shot_cooldown := 0.0
var firing := false
var intro_active := true
var rng := RandomNumberGenerator.new()
var hud: Label
var radar: Control
var gun_mesh: MeshInstance3D
var weapon_index := 1
var weapons := [
    {"name":"PISTOL", "damage":42.0, "rate":0.34, "pellets":1, "spread":0.006, "color":Color(0.12,0.13,0.14), "tone":145.0},
    {"name":"ASSAULT RIFLE", "damage":29.0, "rate":0.105, "pellets":1, "spread":0.012, "color":Color(0.055,0.06,0.065), "tone":92.0},
    {"name":"SHOTGUN", "damage":24.0, "rate":0.76, "pellets":7, "spread":0.075, "color":Color(0.18,0.10,0.045), "tone":58.0}
]
var gun_sounds: Array[AudioStreamWAV] = []
var zombie_sounds: Array[AudioStreamWAV] = []
var music_stream: AudioStreamWAV
var sfx_player: AudioStreamPlayer
var zombie_player: AudioStreamPlayer
var music_player: AudioStreamPlayer

func _ready() -> void:
    rng.randomize()
    model_scene = load("res://assets/CesiumMan.glb")
    build_audio()
    build_environment()
    build_hospital()
    build_player()
    build_hud()
    show_intro()

func mat(color: Color, rough := 0.72, emission := Color.BLACK) -> StandardMaterial3D:
    var m = StandardMaterial3D.new()
    m.albedo_color = color
    m.roughness = rough
    if emission != Color.BLACK:
        m.emission_enabled = true
        m.emission = emission
        m.emission_energy_multiplier = 2.4
    return m

func box(parent: Node3D, pos: Vector3, size: Vector3, material: Material) -> MeshInstance3D:
    var n = MeshInstance3D.new()
    var mesh = BoxMesh.new()
    mesh.size = size
    n.mesh = mesh
    n.position = pos
    n.material_override = material
    parent.add_child(n)
    return n

func synth_sound(seconds: float, kind: int, tone: float) -> AudioStreamWAV:
    var rate := 22050
    var count := int(seconds * rate)
    var bytes := PackedByteArray()
    bytes.resize(count * 2)
    for i in range(count):
        var t := float(i) / rate
        var fade := pow(1.0 - float(i) / count, 1.8)
        var sample := 0.0
        if kind == 0:
            sample = (sin(TAU * tone * t) * 0.45 + sin(TAU * tone * 2.37 * t) * 0.18 + rng.randf_range(-0.38,0.38)) * fade
        elif kind == 1:
            var wobble := tone + sin(t * 9.0) * 17.0
            sample = (sin(TAU * wobble * t) * 0.42 + sin(TAU * wobble * 0.48 * t) * 0.24 + rng.randf_range(-0.12,0.12)) * fade
        else:
            sample = (sin(TAU * tone * t) * 0.28 + sin(TAU * tone * 0.5 * t) * 0.22 + sin(TAU * (tone * 1.5) * t) * 0.12) * (0.55 + 0.45 * sin(t * 2.1))
        bytes.encode_s16(i * 2, int(clamp(sample, -1.0, 1.0) * 32767.0))
    var stream := AudioStreamWAV.new()
    stream.format = AudioStreamWAV.FORMAT_16_BITS
    stream.mix_rate = rate
    stream.stereo = false
    stream.data = bytes
    if kind == 2:
        stream.loop_mode = AudioStreamWAV.LOOP_FORWARD
        stream.loop_begin = 0
        stream.loop_end = count
    return stream

func build_audio() -> void:
    gun_sounds = [synth_sound(.22,0,145.0), synth_sound(.18,0,92.0), synth_sound(.42,0,58.0)]
    zombie_sounds = [synth_sound(1.35,1,64.0), synth_sound(1.75,1,48.0), synth_sound(1.1,1,82.0)]
    music_stream = synth_sound(5.0,2,41.0)
    sfx_player = AudioStreamPlayer.new()
    zombie_player = AudioStreamPlayer.new()
    music_player = AudioStreamPlayer.new()
    sfx_player.volume_db = -2.0
    zombie_player.volume_db = -5.0
    music_player.volume_db = -11.0
    add_child(sfx_player)
    add_child(zombie_player)
    add_child(music_player)

func build_environment() -> void:
    var world = WorldEnvironment.new()
    var env = Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color(0.004,0.007,0.012)
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color(0.14,0.18,0.23)
    env.ambient_light_energy = 0.42
    env.fog_enabled = true
    env.fog_light_color = Color(0.09,0.12,0.15)
    env.fog_density = 0.025
    world.environment = env
    add_child(world)
    var key = DirectionalLight3D.new()
    key.rotation_degrees = Vector3(-55,-28,0)
    key.light_color = Color(0.56,0.68,0.82)
    key.light_energy = 1.15
    key.shadow_enabled = true
    add_child(key)

func build_hospital() -> void:
    var floor_mat = mat(Color(0.16,0.18,0.19))
    var wall_mat = mat(Color(0.43,0.50,0.52))
    var grout = mat(Color(0.035,0.045,0.052))
    var blood = mat(Color(0.22,0.008,0.006),0.46)
    box(self,Vector3(0,-.15,0),Vector3(16,.3,48),floor_mat)
    box(self,Vector3(-8,2.6,0),Vector3(.35,5.2,48),wall_mat)
    box(self,Vector3(8,2.6,0),Vector3(.35,5.2,48),wall_mat)
    box(self,Vector3(0,5.15,0),Vector3(16,.25,48),mat(Color(0.13,0.14,0.15)))
    box(self,Vector3(0,2.6,-24),Vector3(16,5.2,.35),wall_mat)
    for x in range(-6,7,2):
        box(self,Vector3(x,.015,0),Vector3(.035,.035,48),grout)
    for z in range(-22,23,2):
        box(self,Vector3(0,.018,z),Vector3(16,.035,.035),grout)
        box(self,Vector3(-7.80,2.6,z),Vector3(.035,5.1,.035),grout)
        box(self,Vector3(7.80,2.6,z),Vector3(.035,5.1,.035),grout)
    for y in range(1,5):
        box(self,Vector3(-7.79,float(y),0),Vector3(.04,.035,48),grout)
        box(self,Vector3(7.79,float(y),0),Vector3(.04,.035,48),grout)
    for z in range(-20,21,6):
        box(self,Vector3(0,4.95,z),Vector3(2.4,.08,.5),mat(Color(0.86,0.91,0.89),.25,Color(0.55,0.66,0.70)))
        var lamp = OmniLight3D.new()
        lamp.position = Vector3(0,4.55,z)
        lamp.light_color = Color(0.67,0.80,0.86)
        lamp.light_energy = 2.0
        lamp.omni_range = 7
        lamp.shadow_enabled = (z % 12 == 0)
        add_child(lamp)
    box(self,Vector3(-5.7,.55,-6),Vector3(1.4,1.1,1.4),mat(Color(0.27,0.16,0.07)))
    box(self,Vector3(5.9,.65,9),Vector3(1.0,1.3,1.0),mat(Color(0.20,0.11,0.055)))
    box(self,Vector3(-1.5,.025,-4),Vector3(2.3,.04,.75),blood)
    box(self,Vector3(2.2,.028,-12),Vector3(1.5,.04,.55),blood)
    var fire = OmniLight3D.new()
    fire.position = Vector3(0,1.2,-21.5)
    fire.light_color = Color(1,.16,.03)
    fire.light_energy = 4.5
    fire.omni_range = 10
    add_child(fire)
    for i in range(7):
        box(self,Vector3(-2.4+i*.8,.5+abs(sin(i))*.5,-22.3),Vector3(.35,1.1+abs(cos(i))*.7,.35),mat(Color(1,.08+.07*(i%2),.005),.4,Color(1,.04,.001)))

func build_player() -> void:
    player = CharacterBody3D.new()
    player.position = Vector3(0,1,15)
    add_child(player)
    var shape = CollisionShape3D.new()
    var capsule = CapsuleShape3D.new()
    capsule.height = 1.75
    capsule.radius = .38
    shape.shape = capsule
    player.add_child(shape)
    camera = Camera3D.new()
    camera.position = Vector3(0,.72,0)
    camera.fov = 72
    player.add_child(camera)
    gun_mesh = box(camera,Vector3(.32,-.30,-.78),Vector3(.18,.18,.82),mat(weapons[weapon_index].color,.28))
    gun_mesh.rotation_degrees.x = -3

func build_hud() -> void:
    var layer = CanvasLayer.new()
    add_child(layer)
    hud = Label.new()
    hud.position = Vector2(22,18)
    hud.add_theme_font_size_override("font_size",25)
    layer.add_child(hud)
    var title = Label.new()
    title.text = "DEAD ZONE 2.1"
    title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    title.position = Vector2(440,18)
    title.size = Vector2(400,50)
    title.add_theme_font_size_override("font_size",28)
    title.modulate = Color(.88,.12,.14)
    layer.add_child(title)
    radar = Control.new()
    radar.position = Vector2(1050,30)
    radar.size = Vector2(190,190)
    radar.draw.connect(draw_radar)
    layer.add_child(radar)
    for i in range(weapons.size()):
        var button = Button.new()
        button.text = weapons[i].name
        button.position = Vector2(875 + i * 130, 645)
        button.size = Vector2(122,52)
        button.add_theme_font_size_override("font_size",14)
        button.pressed.connect(select_weapon.bind(i))
        layer.add_child(button)

func show_intro() -> void:
    var intro = CanvasLayer.new()
    intro.layer = 20
    add_child(intro)
    var shade = ColorRect.new()
    shade.color = Color(0.005,0.005,0.008,1)
    shade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    intro.add_child(shade)
    var warning = Label.new()
    warning.text = "THE INFECTION HAS SPREAD"
    warning.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    warning.position = Vector2(240,235)
    warning.size = Vector2(800,70)
    warning.add_theme_font_size_override("font_size",30)
    warning.modulate = Color(.72,.72,.72)
    intro.add_child(warning)
    var logo = Label.new()
    logo.text = "DEAD ZONE"
    logo.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    logo.position = Vector2(140,300)
    logo.size = Vector2(1000,110)
    logo.add_theme_font_size_override("font_size",72)
    logo.modulate = Color(.82,.025,.02)
    intro.add_child(logo)
    var mission = Label.new()
    mission.text = "SURVIVE THE NIGHT  •  NO ONE IS COMING"
    mission.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    mission.position = Vector2(240,410)
    mission.size = Vector2(800,60)
    mission.add_theme_font_size_override("font_size",19)
    mission.modulate = Color(.8,.68,.52)
    intro.add_child(mission)
    music_player.stream = music_stream
    music_player.play()
    warning.modulate.a = 0.0
    logo.modulate.a = 0.0
    mission.modulate.a = 0.0
    var tween = create_tween()
    tween.tween_property(warning,"modulate:a",1.0,.8)
    tween.tween_property(logo,"modulate:a",1.0,1.0)
    tween.tween_property(mission,"modulate:a",1.0,.7)
    tween.tween_interval(1.4)
    tween.tween_property(shade,"color:a",0.0,1.1)
    tween.parallel().tween_property(warning,"modulate:a",0.0,.7)
    tween.parallel().tween_property(logo,"modulate:a",0.0,.7)
    tween.parallel().tween_property(mission,"modulate:a",0.0,.7)
    tween.tween_callback(finish_intro.bind(intro))

func finish_intro(intro: CanvasLayer) -> void:
    intro_active = false
    music_player.stop()
    intro.queue_free()

func select_weapon(index: int) -> void:
    weapon_index = index
    gun_mesh.material_override = mat(weapons[index].color,.28)
    if index == 0:
        gun_mesh.scale = Vector3(.8,.8,.62)
    elif index == 1:
        gun_mesh.scale = Vector3.ONE
    else:
        gun_mesh.scale = Vector3(1.18,1.15,1.22)

func draw_radar() -> void:
    var center = Vector2(95,95)
    radar.draw_circle(center,90,Color(0.01,0.025,0.035,.88))
    radar.draw_arc(center,90,0,TAU,64,Color(.5,.62,.66),3)
    radar.draw_circle(center+Vector2(player.position.x/8*76,-player.position.z/24*76),6,Color(.1,1,.3))
    for z in zombies:
        if is_instance_valid(z):
            radar.draw_circle(center+Vector2(z.position.x/8*76,-z.position.z/24*76),5,Color(1,.05,.03))

func spawn_zombie() -> void:
    if model_scene == null:
        return
    var body = CharacterBody3D.new()
    body.position = Vector3(rng.randf_range(-6.5,6.5),0,rng.randf_range(-22,-7))
    body.set_meta("hp",100.0+wave*10)
    add_child(body)
    var visual = model_scene.instantiate()
    visual.scale = Vector3.ONE * 1.05
    visual.rotation_degrees.y = 180
    body.add_child(visual)
    tint_infected(visual,rng.randi_range(0,2))
    var col = CollisionShape3D.new()
    var cap = CapsuleShape3D.new()
    cap.height = 1.75
    cap.radius = .38
    col.shape = cap
    col.position.y = .9
    body.add_child(col)
    for child in visual.find_children("*","AnimationPlayer",true,false):
        var names = child.get_animation_list()
        if names.size() > 0:
            child.play(names[0])
    zombies.append(body)
    if not zombie_player.playing or rng.randf() > .6:
        zombie_player.stream = zombie_sounds[rng.randi_range(0,zombie_sounds.size()-1)]
        zombie_player.pitch_scale = rng.randf_range(.78,1.08)
        zombie_player.play()

func tint_infected(root: Node, variant: int) -> void:
    var colors = [Color(.32,.42,.26),Color(.50,.48,.39),Color(.31,.20,.18)]
    var infected = mat(colors[variant],.82)
    for mesh in root.find_children("*","MeshInstance3D",true,false):
        mesh.material_override = infected

func _physics_process(delta: float) -> void:
    if intro_active:
        return
    shot_cooldown = max(0.0,shot_cooldown-delta)
    spawn_timer -= delta
    if remaining > 0 and spawn_timer <= 0:
        spawn_zombie()
        remaining -= 1
        spawn_timer = .72
    if remaining == 0 and zombies.is_empty():
        wave += 1
        remaining = 6 + wave * 2
        spawn_timer = 2.0
    var input = Vector2.ZERO
    if move_touch >= 0:
        input = (move_now-move_origin)/90.0
        input = input.limit_length(1)
    var forward = Vector3(-sin(yaw),0,-cos(yaw))
    var right = Vector3(cos(yaw),0,-sin(yaw))
    player.velocity = (forward*-input.y+right*input.x)*4.5
    player.move_and_slide()
    for i in range(zombies.size()-1,-1,-1):
        var z = zombies[i]
        if not is_instance_valid(z):
            zombies.remove_at(i)
            continue
        var dir = player.global_position-z.global_position
        dir.y = 0
        if dir.length() > 1.2:
            z.velocity = dir.normalized()*(1.25+wave*.04)
            z.look_at(Vector3(player.global_position.x,z.global_position.y,player.global_position.z),Vector3.UP)
            z.move_and_slide()
        else:
            z.velocity = Vector3.ZERO
    if firing and shot_cooldown <= 0:
        shoot()
        shot_cooldown = float(weapons[weapon_index].rate)
    hud.text = "WAVE %d  •  INFECTED %d  •  %s" % [wave,zombies.size(),weapons[weapon_index].name]
    radar.queue_redraw()

func shoot() -> void:
    sfx_player.stream = gun_sounds[weapon_index]
    sfx_player.pitch_scale = rng.randf_range(.94,1.06)
    sfx_player.play()
    var weapon = weapons[weapon_index]
    for pellet in range(int(weapon.pellets)):
        var from = camera.global_position
        var direction = -camera.global_transform.basis.z
        direction += camera.global_transform.basis.x * rng.randf_range(-weapon.spread,weapon.spread)
        direction += camera.global_transform.basis.y * rng.randf_range(-weapon.spread,weapon.spread)
        var q = PhysicsRayQueryParameters3D.create(from,from+direction.normalized()*80)
        q.exclude = [player.get_rid()]
        var hit = get_world_3d().direct_space_state.intersect_ray(q)
        if hit and hit.collider in zombies:
            var z = hit.collider
            z.set_meta("hp",float(z.get_meta("hp"))-float(weapon.damage))
            if float(z.get_meta("hp")) <= 0:
                zombies.erase(z)
                z.queue_free()

func _unhandled_input(event: InputEvent) -> void:
    if intro_active:
        return
    if event is InputEventScreenTouch:
        if event.pressed:
            if event.position.x < get_viewport().get_visible_rect().size.x*.45:
                move_touch = event.index
                move_origin = event.position
                move_now = event.position
            else:
                look_touch = event.index
                firing = true
        else:
            if event.index == move_touch:
                move_touch = -1
            if event.index == look_touch:
                look_touch = -1
                firing = false
    elif event is InputEventScreenDrag:
        if event.index == move_touch:
            move_now = event.position
        elif event.index == look_touch:
            yaw -= event.relative.x*.004
            pitch = clamp(pitch-event.relative.y*.003,-1.0,1.0)
            player.rotation.y = yaw
            camera.rotation.x = pitch
    elif event is InputEventMouseMotion and Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
        yaw -= event.relative.x*.003
        pitch = clamp(pitch-event.relative.y*.003,-1.0,1.0)
        player.rotation.y = yaw
        camera.rotation.x = pitch
    elif event is InputEventMouseButton:
        firing = event.pressed
