;##############################################################################################################################
; Realm Crafter version 1.10																									
; Copyright (C) 2007 Solstar Games, LLC. All rights reserved																	
; contact@solstargames.com																																																		
;																																																																#
; Programmer: Rob Williams																										
; Program: Realm Crafter Actors module
;																																
;This is a licensed product:
;BY USING THIS SOURCECODE, YOU ARE CONFIRMING YOUR ACCEPTANCE OF THE SOFTWARE AND AGREEING TO BECOME BOUND BY THE TERMS OF 
;THIS AGREEMENT. IF YOU DO NOT AGREE TO BE BOUND BY THESE TERMS, THEN DO NOT USE THE SOFTWARE.
;																		
;Licensee may NOT: 
; (i)   create any derivative works of the Engine, including translations Or localizations, other than Games;
; (ii)  redistribute, encumber, sell, rent, lease, sublicense, Or otherwise transfer rights To the Engine; or
; (iii) remove Or alter any trademark, logo, copyright Or other proprietary notices, legends, symbols Or labels in the Engine.
; (iv)   licensee may Not distribute the source code Or documentation To the engine in any manner, unless recipient also has a 
;       license To the Engine.													
; (v)  use the Software to develop any software or other technology having the same primary function as the Software, 
;       including but not limited to using the Software in any development or test procedure that seeks to develop like 
;       software or other technology, or to determine if such software or other technology performs in a similar manner as the
;       Software																																
;##############################################################################################################################
; Realm Crafter Client by Rob W (rottbott@hotmail.com), August 2004

; Variables -------------------------------------------------------------------------------------------------------------------------

Global PlayerTarget, ActorSelectEN, ClickMarkerEN, ClickMarkerTimer ; Interface
Global GameName$, UpdateGame$, UpdateMusic ; General settings
Global ServerHost$, UpdateHost$, ServerPort, Connection, PeerToHost ; Network
Global Cam, CamDist# = 10.0, CamPitch#, CamYaw#, CamMode, CamHeight# ; Camera
Global DefaultLight.Light ; Default white directional light
Global Me.ActorInstance, SelectedCharacter ; Local player
Global QuestLog.QuestLog = New QuestLog
Global AreaName$, CurrentAreaID, PvPEnabled ; Zone
Global FlashEN, FlashStart, FlashLength, Flashing ; Screen flash effect
Global QuitActive, QuitActiveMS, QuitTimer, QuitTimerMS, QuitComplete, QuitBar, QuitLabel ; Quit game mode
Global PlayerHasTouchedDown = True ; To prevent repeated jumping while already in the air
Global GrassEnabled = True ; Grass and swaying trees
Global ZonedMS ; Timer to squelch login messages for 5 seconds after zoning
Global RequireMemorise ; Must abilities be memorised before use?
Global RandomImages
Global FullScreen%, VSync%

Const CameraViewRange# = 2000

Global InteractRange# = 20.0 ; Range at which we can interact with an ActorInstance
; don't forget to update InteractDist in the servers actors.bb file to be InteractRange * InteractRange



; Money
Global Money1$, Money2$, Money3$, Money4$
Global Money2x, Money3x, Money4x
; Stats
Global SpeedStat, StrengthStat, HealthStat, EnergyStat = -1, BreathStat = -1
; Delta timing stuff
Const DeltaFrames = 6 ; How many frames to take the average of to get the current delta
Dim DeltaBuffer(DeltaFrames - 1)
For i = 0 To DeltaFrames - 1
	DeltaBuffer(i) = 35
Next
Global fps#, Delta#
Const BaseFramerate# = 30.0
; Network timing
Const NetworkMS = 1000 / 5
Global LastNetwork
; Collision types
Const C_None      = 0
Const C_Sphere    = 1
Const C_Box       = 2
Const C_Triangle  = 3
Const C_Actor     = 4
Const C_Player    = 5
Const C_Cam       = 6
Const C_ActorTri1 = 7
Const C_ActorTri2 = 8
; Gravity settings
Global Gravity# = 0.0125
Const JumpStrength# = 8.0
; Distance moved when moving forwards/backwards with keyboard (higher = more smooth over network but less fine control)
Const KeyboardMoveDistance# = 3.0
; General purpose pivot
Global GPP
; Other entities
Global LootBagEN

; Includes --------------------------------------------------------------------------------------------------------------------------

Include "Modules\Language.bb"              ; Language module (allows users to change string constants)
Include "Modules\RottParticles.bb"         ; Particle system
Include "Modules\RCEnet.bb"                ; Network library
Include "Modules\Media.bb"                 ; Game media module
Include "Modules\Environment.bb"           ; Environment
Include "Modules\Environment3D.bb"         ; Environment 3D stuff
Include "Modules\Animations.bb"            ; Actor animations
Include "Modules\Spells.bb"                ; Spells (abilities) module
Include "Modules\Items.bb"                 ; Items
Include "Modules\Inventories.bb"           ; Inventory
Include "Modules\Actors.bb"                ; Actors
Include "Modules\Actors3D.bb"              ; Actors 3D stuff
Include "Modules\ClientCombat.bb"          ; Combat module
Include "Modules\Interface.bb"             ; In-game user interface
Include "Modules\Interface3D.bb"           ; In-game user interface 3D stuff
Include "Modules\Radar.bb"                 ; RifRaf's radar
Include "Modules\RCTrees.bb"               ; RifRaf's trees/grass
Include "Modules\ClientAreas.bb"           ; Area/zone module
Include "Modules\Logging.bb"               ; Logging
Include "Modules\ClientLoaders.bb"         ; Client startup module
Include "Modules\ClientNet.bb"             ; Client specific network module
Include "Modules\MainMenu.bb"              ; Game menu
Include "Modules\Packets.bb"               ; Packet types
Include "Modules\Gooey.bb"                 ; Gooey GUI
Include "Modules\MD5.bb"                   ; MD5 function for passwords
Include "Modules\Projectiles3D.bb"         ; Projectile 3D display system
Include "Modules\Utility.bb"         	   ; Projectile 3D display system


; Start client ----------------------------------------------------------------------------------------------------------------------

SeedRnd MilliSecs()
Global MainLog = StartLog("Client Log", False)
LoadOptions()
RunMenu()
LoadGame()
Connect()

; Fixed attributes needed by engine
F = ReadFile("Data\Game Data\Fixed Attributes.dat")
If F = 0 Then RuntimeError("Could not open Data\Game Data\Fixed Attributes.dat!")
HealthStat = ReadShort(F)
EnergyStat = ReadShort(F)
BreathStat = ReadShort(F)
StrengthStat = ReadShort(F)
SpeedStat = ReadShort(F)
CloseFile(F)
If HealthStat = 65535 Then RuntimeError("A valid Health attribute must be selected!")
If EnergyStat = 65535 Then EnergyStat = -1
If BreathStat = 65535 Then BreathStat = -1
If StrengthStat = 65535 Then RuntimeError("A valid Strength attribute must be selected!")
If SpeedStat = 65535 Then RuntimeError("A valid Speed attribute must be selected!")

WriteLog(MainLog, "Game started")

; Main loop -------------------------------------------------------------------------------------------------------------------------

DeltaTime = MilliSecs()
LastNetwork = MilliSecs()
Repeat

	; Delta timing bits
	DeltaTime = MilliSecs() - DeltaTime
	DeltaBuffer(DeltaBufferIndex) = DeltaTime
	DeltaBufferIndex = DeltaBufferIndex + 1
	If DeltaBufferIndex >= DeltaFrames Then DeltaBufferIndex = 0

	; Take average of last N frames to get delta time coefficient
	Time# = 0.0
	For i = 0 To DeltaFrames - 1
		Time# = Time# + DeltaBuffer(i)
	Next
	Time# = Time# / Float#(DeltaFrames)
	FPS# = 1000.0 / Time#
	Delta# = BaseFramerate# / FPS#
	DeltaTime = MilliSecs()
	If Delta# > 3.5 Then Delta# = 3.5 ; Don't let delta go too OTT

	PreRenderWorld()	; DebugBanner update
	; Update screen
	Flip(VSync)

	; Render game
	CameraProjMode(GY_Cam, 0)
	RenderWorld()
	CameraProjMode(GY_Cam, 1)
	; Render GUI
	CameraProjMode(Cam, 0)
	For S.Scenery = Each Scenery
		HideEntity(S\EN)
	Next
	For T.Terrain = Each Terrain
		HideEntity(T\EN)
	Next
	RenderWorld()
	CameraProjMode(Cam, 1)
	For S.Scenery = Each Scenery
		ShowEntity(S\EN)
	Next
	For T.Terrain = Each Terrain
		ShowEntity(T\EN)
	Next
	PostRenderWorld()	; DebugBanner update
	UpdateFPS()

	; Update game
	UpdateCombat()                 ; Attack my target etc.
	UpdateInterface()              ; Interface
	UpdateActorInstances()         ; Actor movement etc.
	UpdateEnvironment()            ; Time of day etc.
	UpdateEnvironment3D()          ; Weather, skyspheres, suns etc.
	UpdateLights()                 ; Updates fade in/out of dynamic lights
	UpdateProjectiles()            ; Projectile instances
	UpdateSoundZones()             ; Sound zones
	RP_Update(Delta#)              ; RottParticles
	UpdateAnimatedScenery()        ; Animated scenery
	If GrassEnabled = True
		UpdateTrees(Cam, Delta#)   ; RifRaf's tree/grass system
	EndIf
	UpdateRadar(Me\CollisionEN)    ; RifRaf's radar system
	If Flashing = True
		UpdateScreenFlash()        ; Screen flash effect
	EndIf
	If DamageInfoStyle = 3
		UpdateFloatingNumbers()    ; Combat damage information
	EndIf
	UpdateWorld(Delta#)            ; Animation, collision etc.
	UpdateSwimmingActorInstances() ; Updates actors who are underwater, in case UpdateWorld's collision has moved them above the surface
	UpdateRidingActorInstances()   ; Updates actors on horseback who don't have their own collision
	UpdateCamera()                 ; Camera movement
	UpdateLensFlare()              ; Lens flares from suns

	; Update network
	RCE_Update()
	RCE_CreateMessages()
	
	UpdateNetwork()

Until QuitComplete = True

; Close client
RCE_Disconnect()
WriteLog(MainLog, "Client closed")
End

; Functions -------------------------------------------------------------------------------------------------------------------------

; Updates movement etc. for all actor instances
Function UpdateActorInstances()

	SetPickModes()

	; Updates for every actor
	For AI.ActorInstance = Each ActorInstance

		; If it's not dead
		If AI\Attributes\Value[HealthStat] > 0
			; Fade in (uses \AIMode field which is a server field, to save adding an extra field and using server memory)
			; DISABLE - uses entityautofade instead
;			If AI\AIMode < 501
;				If AI = Me Or AI = Me\Mount Then AI\AIMode = 499 ; The player and the player's mount do not fade in
;				AI\AIMode = AI\AIMode + 2
;				Alpha# = Float#(AI\AIMode) / 501.0
;				EntityAlpha AI\EN, Alpha#
;				EntityAlpha AI\ShadowEN, Alpha#
;				If AI\WeaponEN <> 0 Then EntityAlpha AI\WeaponEN, Alpha#
;				If AI\ShieldEN <> 0 Then EntityAlpha AI\ShieldEN, Alpha#
;				If AI\HatEN <> 0 Then EntityAlpha AI\HatEN, Alpha#
;			EndIf

			; Check if this actor is underwater
			AI\Underwater = 0
			If AI\Actor\Environment = Environment_Amphibious
				For W.Water = Each Water
					If EntityY#(AI\CollisionEN) < EntityY#(W\EN) - 0.5
						If Abs(EntityX#(AI\CollisionEN) - EntityX#(W\EN)) < (W\ScaleX# / 2.0)
							If Abs(EntityZ#(AI\CollisionEN) - EntityZ#(W\EN)) < (W\ScaleZ# / 2.0)
								AI\Underwater = Handle(W)
								PositionEntity(AI\EN, 0, 0, 0)
								Exit
							EndIf
						EndIf
					EndIf
				Next
			EndIf
			If AI\Underwater = 0 Then PositionEntity(AI\EN, 0, MeshHeight#(AI\EN) / -2.0, 0)

			; Show/hide nametags in HideNametags mode 2 (selected only)
			If HideNametags = 2
				If AI\NametagEN <> 0
					If Handle(AI) = PlayerTarget
						ShowEntity(AI\NametagEN)
					Else
						HideEntity(AI\NametagEN)
					EndIf
				EndIf
			EndIf

			; Update only if this actor is not riding a mount
			If AI\Mount = Null
				; Hide nametag if this actor is a mount
				If AI\NametagEN <> 0 And HideNametags = 0
					If AI\Rider = Null
						ShowEntity(AI\NametagEN)
					Else
						HideEntity(AI\NametagEN)
					EndIf
				EndIf

				; Gravity
				If AI\Actor\Environment <> Environment_Swim And AI\Actor\Environment <> Environment_Fly And AI\Underwater = 0
					AI\Y# = AI\Y# - (Gravity# * Delta#)
					; Reset velocity on contact with ground
					If AI\Y# < 0.0
						For i = 1 To CountCollisions(AI\CollisionEN)
							If CollisionNY#(AI\CollisionEN, i) > SlopeRestrict#
								AI\Y# = 0.0
								; Remember when the player touches down after a jump
								If AI = Me Then PlayerHasTouchedDown = True
								Exit
							EndIf
						Next
					EndIf
					TranslateEntity AI\CollisionEN, 0.0, AI\Y#, 0.0
				EndIf

				; Correct to server Y position for flying actors
				If AI\Actor\Environment = Environment_Fly And AI <> Me And AI <> Me\Mount
					YPos# = CurveValue(EntityY#(AI\CollisionEN), AI\Y#, 7.0)
					PositionEntity(AI\CollisionEN, EntityX#(AI\CollisionEN), YPos#, EntityZ#(AI\CollisionEN))
				EndIf

				; Movement/animation
				PositionEntity GPP, AI\DestX#, EntityY#(AI\CollisionEN), AI\DestZ#
				If EntityDistance#(AI\CollisionEN, GPP) > 2.0

					; Smoothly correct to server position
					XPos# = CurveValue(EntityX#(AI\CollisionEN), AI\X#, 20.0)
					ZPos# = CurveValue(EntityZ#(AI\CollisionEN), AI\Z#, 20.0)
					PositionEntity AI\CollisionEN, XPos#, EntityY#(AI\CollisionEN), ZPos#

					; Emergency reset if stuck somewhere
					; DISABLE
					; Code disabled, as the actors are meant to vanish when out of syncronization range
;					XDist# = Abs(XPos# - AI\X#)
;					ZDist# = Abs(ZPos# - AI\Z#)
;					Dist# = (XDist * XDist#) + (ZDist# * ZDist#)
;					If Dist# > CameraViewDistance + 50
;						If AI <> Me And AI <> Me\Mount
;							PositionEntity AI\CollisionEN, AI\X#, EntityY#(AI\CollisionEN) + 10.0, AI\Z#
;							Result = LinePick(AI\X#, EntityY#(AI\CollisionEN) + 10000.0, AI\Z#, 0.0, -20000.0, 0.0)
;							If Result <> 0 Then PositionEntity AI\CollisionEN, AI\X#, PickedY#() + (MeshHeight#(AI\EN) * 0.05), AI\Z#
;							ResetEntity(AI\CollisionEN)
;						EndIf
;					EndIf

					; Calculate speed
					Speed# = 1.5 * (Float#(AI\Attributes\Value[SpeedStat]) / Float#(AI\Attributes\Maximum[SpeedStat])) * Delta#
					If AI\IsRunning = True
						Speed# = Speed# * 2.0
					ElseIf AI\WalkingBackward = True
						Speed# = Speed# / 2.0
					EndIf

					; Move
					If dist# < CameraViewRange Then PointEntity(AI\CollisionEN, GPP) ; orig < 1000		; Fix to remove the horizontally floating actor issue - found and submitted by atulos
					MoveEntity(AI\CollisionEN, 0.0, 0.0, Speed#)
					
					If AI\AITarget <> Null Then PointEntity (AI\CollisionEN, AI\AITarget\CollisionEN) ; fix to always orient actors towards their target
					If AI\WalkingBackward = False Then TurnEntity(AI\CollisionEN, 0.0, 180.0, 0.0)

					; If actor is not already animating, animate it
					Seq = CurrentSeq(AI)
					If Seq <= Anim_Idle Or Animating(AI\EN) = False Or Seq = Anim_RideIdle Or Seq = Anim_SwimIdle
						If AI\Underwater = 0
							If AI\IsRunning = True
								PlayAnimation(AI, 1, 0.05, Anim_Run)
								If AI\Rider <> Null
									If CurrentSeq(AI\Rider) <> Anim_RideRun Then PlayAnimation(AI\Rider, 1, 0.05, Anim_RideRun)
								EndIf
							Else
								If AI\WalkingBackward = False
									PlayAnimation(AI, 1, 0.04, Anim_Walk)
								Else
									PlayAnimation(AI, 1, -0.02, Anim_Walk)
								EndIf
								If AI\Rider <> Null
									If CurrentSeq(AI\Rider) <> Anim_RideWalk Then PlayAnimation(AI\Rider, 1, 0.05, Anim_RideWalk)
								EndIf
							EndIf
						Else
							If AI\IsRunning = True
								PlayAnimation(AI, 1, 0.05, Anim_SwimFast)
								If AI\Rider <> Null
									If CurrentSeq(AI\Rider) <> Anim_RideRun Then PlayAnimation(AI\Rider, 1, 0.05, Anim_RideRun)
								EndIf
							Else
								PlayAnimation(AI, 1, 0.025, Anim_SwimSlow)
								If AI\Rider <> Null
									If CurrentSeq(AI\Rider) <> Anim_RideWalk Then PlayAnimation(AI\Rider, 1, 0.05, Anim_RideWalk)
								EndIf
							EndIf
						EndIf
					; If the actor is animating, check it's doing the right one
					ElseIf Seq >= Anim_RideRun
						If AI\Underwater = 0
							If AI\IsRunning = True And Seq <> Anim_Run
								PlayAnimation(AI, 1, 0.05, Anim_Run)
								If AI\Rider <> Null
									If CurrentSeq(AI\Rider) <> Anim_RideRun Then PlayAnimation(AI\Rider, 1, 0.05, Anim_RideRun)
								EndIf
							ElseIf AI\IsRunning = False And Seq <> Anim_Walk
								If AI\WalkingBackward = False
									PlayAnimation(AI, 1, 0.04, Anim_Walk)
								Else
									PlayAnimation(AI, 1, -0.02, Anim_Walk)
								EndIf
								If AI\Rider <> Null
									If CurrentSeq(AI\Rider) <> Anim_RideWalk Then PlayAnimation(AI\Rider, 1, 0.05, Anim_RideWalk)
								EndIf
							EndIf
						Else
							If AI\IsRunning = True And Seq <> Anim_SwimFast
								PlayAnimation(AI, 1, 0.05, Anim_SwimFast)
								If AI\Rider <> Null
									If CurrentSeq(AI\Rider) <> Anim_RideRun Then PlayAnimation(AI\Rider, 1, 0.05, Anim_RideRun)
								EndIf
							ElseIf AI\IsRunning = False And Seq <> Anim_SwimSlow
								PlayAnimation(AI, 1, 0.025, Anim_SwimSlow)
								If AI\Rider <> Null
									If CurrentSeq(AI\Rider) <> Anim_RideWalk Then PlayAnimation(AI\Rider, 1, 0.05, Anim_RideWalk)
								EndIf
							EndIf
						EndIf
					EndIf

					; Footstep sound effects
					If AI\Underwater = 0
						AnimT# = AnimTime#(AI\EN)
						If AnimT# < 1.0 Or AnimT# > Float#(AnimLength(AI\EN) - 1) Or Abs(AnimT# - (Float#(AnimLength(AI\EN)) / 2.0)) < 2.0
							If AI\FootstepPlayedThisCycle = False
								If AI\Gender = 0
									If CurrentWeather = W_Rain Or CurrentWeather = W_Storm
										Snd = GetSound(AI\Actor\MSpeechIDs[Speech_FootstepWet])
									Else
										Snd = GetSound(AI\Actor\MSpeechIDs[Speech_FootstepDry])
									EndIf
								Else
									If CurrentWeather = W_Rain Or CurrentWeather = W_Storm
										Snd = GetSound(AI\Actor\FSpeechIDs[Speech_FootstepWet])
									Else
										Snd = GetSound(AI\Actor\FSpeechIDs[Speech_FootstepDry])
									EndIf
								EndIf
								EmitSound(Snd, AI\EN)
								AI\FootstepPlayedThisCycle = True
							EndIf
						Else
							AI\FootstepPlayedThisCycle = False
						EndIf
					EndIf
				Else
					AI\IsRunning = False
					If AI\Underwater = 0
						; Random yawn/stretch
						If Rand(1, 1000) = 1 And AnimSeq(AI\EN) = AI\AnimSeqs[Anim_Idle] And AI\Rider = Null
							PlayAnimation(AI, 3, 0.007, Rand(Anim_LookRound, Anim_Yawn))
						EndIf
						; Idle animation
						If (CurrentSeq(AI) >= Anim_RideRun Or Animating(AI\EN) = False) And AnimSeq(AI\EN) <> AI\AnimSeqs[Anim_Idle]
							Animate(AI\EN, 0) ; Workaround for a model bug - unfortunately causes jerky movement
							PlayAnimation(AI, 1, 0.003, Anim_Idle)
						EndIf
						; Rider idle animation
						If AI\Rider <> Null
							If CurrentSeq(AI\Rider) <> Anim_RideIdle Then PlayAnimation(AI\Rider, 1, 0.003, Anim_RideIdle)
						EndIf
					Else
						; Underwater idle animation
						If (CurrentSeq(AI) >= Anim_RideRun Or CurrentSeq(AI) = Anim_Idle Or Animating(AI\EN) = False) And AnimSeq(AI\EN) <> AI\AnimSeqs[Anim_SwimIdle]
							PlayAnimation(AI, 1, 0.005, Anim_SwimIdle)
							If AI\Rider <> Null
								If CurrentSeq(AI\Rider) <> Anim_RideIdle Then PlayAnimation(AI\Rider, 1, 0.003, Anim_RideIdle)
							EndIf
						EndIf
					EndIf
				EndIf
			EndIf

			; Shadow (display only on actors who are not riding a mount)
			If AI\Mount = Null And AI\Underwater = 0
				Result = LinePick(EntityX#(AI\CollisionEN), EntityY#(AI\CollisionEN), EntityZ#(AI\CollisionEN), 0.0, -200.0, 0.0)
				If Result <> 0
					PositionEntity(AI\ShadowEN, PickedX#(), PickedY#(), PickedZ#())
					AlignToVector(AI\ShadowEN, PickedNX#(), PickedNY#(), PickedNZ#(), 2)
					RotateEntity(AI\ShadowEN, EntityPitch#(AI\ShadowEN), EntityYaw#(AI\CollisionEN), EntityRoll#(AI\ShadowEN))
					MoveEntity(AI\ShadowEN, 0, 0.095, 0)
					ShowEntity(AI\ShadowEN)
				Else
					HideEntity(AI\ShadowEN)
				EndIf
			Else
				HideEntity(AI\ShadowEN)
			EndIf

			; Nametag
			If AI\NametagEN <> 0 Then PointEntity(AI\NametagEN, Cam)


			; FIXME - This enforce costs a lot of performance (10%++) without offering really anything.
			; Hide if not in view to stop animations being updated
			If EntityInView(AI\EN, Cam) = False
				HideEntity(AI\EN)
			Else
				ShowEntity(AI\EN)
			EndIf
		; If it is dead
		Else
			; Apply gravity to flying/swimming creatures
			If AI\Actor\Environment = Environment_Swim Or AI\Actor\Environment = Environment_Fly
				AI\Y# = AI\Y# - (Gravity# * Delta#)
				; Reset velocity on contact with ground
				If AI\Y# < 0.0
					For i = 1 To CountCollisions(AI\CollisionEN)
						If CollisionNY#(AI\CollisionEN, i) > SlopeRestrict#
							AI\Y# = 0.0
							Exit
						EndIf
					Next
				EndIf
				TranslateEntity AI\CollisionEN, 0.0, AI\Y#, 0.0
			EndIf

			; NPCs only fade out
			If Animating(AI\EN) = False And AI\RNID = 0
				If AI\AIMode > 0
					AI\AIMode = AI\AIMode - 1
					Alpha# = Float#(AI\AIMode) / 501.0
					EntityAlpha AI\EN, Alpha#
					If AI\WeaponEN <> 0 Then EntityAlpha AI\WeaponEN, Alpha#
					If AI\ShieldEN <> 0 Then EntityAlpha AI\ShieldEN, Alpha#
					If AI\HatEN <> 0 Then EntityAlpha AI\HatEN, Alpha#
				Else
					SafeFreeActorInstance(AI)
				EndIf
			EndIf
		EndIf
	Next

End Function

; Updates actors on horseback who don't have their own collision
Function UpdateRidingActorInstances()

	; Go through again and update positions of actors riding mounts
	For AI.ActorInstance = Each ActorInstance
		If AI\Mount <> Null
			; Move the rider to the saddle position
			SaddleEN = FindChild(AI\Mount\EN, "Mount")
			If SaddleEN = 0 Then SaddleEN = AI\Mount\CollisionEN
			PositionEntity(AI\CollisionEN, EntityX#(SaddleEN, True), EntityY#(SaddleEN, True), EntityZ#(SaddleEN, True))
			TranslateEntity(AI\CollisionEN, 0, EntityY#(AI\CollisionEN) - EntityY#(AI\EN, True), 0)
			RotateEntity(AI\CollisionEN, 0.0, EntityYaw#(AI\Mount\CollisionEN), 0.0)
		EndIf
	Next

End Function

; Updates actors who are underwater, in case UpdateWorld's collision has moved them above the surface
Function UpdateSwimmingActorInstances()

	; Cycle through each underwater actor
	For AI.ActorInstance = Each ActorInstance
		If AI\Underwater <> 0
			If AI\Actor\Environment = Environment_Amphibious
				For W.Water = Each Water
					If EntityY#(AI\CollisionEN) > EntityY#(W\EN) - 0.5
						AI\Underwater = 0
						PositionEntity(AI\EN, 0, MeshHeight#(AI\EN) / -2.0, 0)
					EndIf
				Next
			EndIf
		EndIf
	Next

End Function

; Updates the camera
Function UpdateCamera()

	Bonce = FindChild(Me\EN, "Head")
	If Bonce = 0 Then RuntimeError(Me\Actor\Race$ + " actor mesh is missing a 'Head' joint!")

	; Third person
	If CamMode = 0
		; Ugly hack to allow repetion of camera positioning if camera ends up too close to the player (see below)
		Repeated = False
		.CameraRepeat
		; Find desired camera position in global units
		CamX# = EntityX#(Cam)
		CamY# = EntityY#(Cam)
		CamZ# = EntityZ#(Cam)
		PlayerX# = EntityX#(Me\CollisionEN)
		PlayerY# = EntityY#(Me\CollisionEN) + CamHeight#
		PlayerZ# = EntityZ#(Me\CollisionEN)
		PositionEntity(Cam, PlayerX#, PlayerY#, PlayerZ#)
		RotateEntity(Cam, CamPitch#, CamYaw# + EntityYaw#(Me\CollisionEN) + 180.0, 0.0)
		MoveEntity(Cam, 0.0, 0.0, -CamDist#)
		DesiredX# = EntityX#(Cam)
		DesiredY# = EntityY#(Cam) + 1.5
		DesiredZ# = EntityZ#(Cam)
		; Camera collision with scenery (ensure player is always visible)
		SetPickModes()
		Result = LinePick(PlayerX#, PlayerY#, PlayerZ#, DesiredX# - PlayerX#, DesiredY# - PlayerY#, DesiredZ# - PlayerZ#)
		If Result <> 0
			DesiredX# = PickedX#()
			DesiredY# = PickedY#()
			DesiredZ# = PickedZ#()
		EndIf
		; Smoothly move camera towards the desired position
		ActualX# = CamX# + (((DesiredX# - CamX#) / 6.0) * Delta#)
		ActualY# = CamY# + (((DesiredY# - CamY#) / 6.0) * Delta#)
		ActualZ# = CamZ# + (((DesiredZ# - CamZ#) / 6.0) * Delta#)
		PositionEntity(Cam, ActualX#, ActualY#, ActualZ#)
		PositionEntity(GPP, PlayerX#, PlayerY#, PlayerZ#)
		PointEntity(Cam, GPP)
		; If the camera has collided, move it forwards so it doesn't clip the object
		If Result <> 0 Then MoveEntity(Cam, 0.0, 0.0, 0.1)
		; Flip camera 180 degrees if pushed too close to the player by a wall (prevents flickering)
		PositionEntity(GPP, DesiredX#, DesiredY#, DesiredZ#)
		If EntityDistance#(GPP, Bonce) < 2.0 And Repeated = False
			CamYaw# = CamYaw# + 180.0
			Repeated = True
			Goto CameraRepeat
			Return
		EndIf
	; First person
	Else
		PositionEntity(Cam, EntityX#(Bonce, True), EntityY#(Bonce, True), EntityZ#(Bonce, True))
		ActualPitch# = CurveValue(EntityPitch#(Cam), -CamPitch#, 1.1)
		RotateEntity(Cam, ActualPitch#, EntityYaw#(Me\CollisionEN) + 180.0, 0.0)
	EndIf

	; Underwater camera effects
	WasUnderwater = CameraUnderwater
	CameraUnderwater = False
	For W.Water = Each Water
		If EntityY#(Cam) < EntityY#(W\EN)
			If Abs(EntityX#(Cam) - EntityX#(W\EN)) < (W\ScaleX# / 2.0)
				If Abs(EntityZ#(Cam) - EntityZ#(W\EN)) < (W\ScaleZ# / 2.0)
					CameraClsColor Cam, W\Red, W\Green, W\Blue
					CameraFogColor Cam, W\Red, W\Green, W\Blue
					FogNearNow# = 1.0 : FogFarNow# = 50.0
					SetViewDistance(Cam, FogNearNow#, FogFarNow#)
					HideEntity SkyEN
					HideEntity StarsEN
					HideEntity CloudEN
					CameraUnderwater = True
					Exit
				EndIf
			EndIf
		EndIf
	Next
	If WasUnderwater = True
		If CameraUnderwater = False
			SetWeather(CurrentWeather)
			ShowEntity SkyEN
			ShowEntity StarsEN
			ShowEntity CloudEN
		EndIf
	EndIf

End Function

; Updates all animated scenery
Function UpdateAnimatedScenery()

	For S.Scenery = Each Scenery
		If S\AnimationMode = 2
			If Animating(S\EN) = False Then Animate(S\EN, 2)
		EndIf
	Next

End Function

; Updates all sound zones
Function UpdateSoundZones()

	For SZ.SoundZone = Each SoundZone
		; I am in the sound zone
		If EntityDistance#(Me\CollisionEN, SZ\EN) < SZ\Radius#
			; Sound has never been played - play it
			If SZ\Channel = 0
				PlaySoundZone(SZ)
			; Channel has stopped playing - play it when repeat timer is done
			ElseIf ChannelPlaying(SZ\Channel) = False
				; Repeat it now
				If SZ\RepeatTime = 0
					PlaySoundZone(SZ)
				; Repeat it on a timer
				ElseIf SZ\RepeatTime > 0
					; Only just stopped, set timer
					If SZ\Timer = 0
						SZ\Timer = MilliSecs()
					; Time has elapsed - play it
					ElseIf MilliSecs() - SZ\Timer > SZ\RepeatTime * 1000
						SZ\Timer = 0
						PlaySoundZone(SZ)
					EndIf
				EndIf
			EndIf
		; I am outside it but it is playing - fade it out
		ElseIf ChannelPlaying(SZ\Channel)
			If SZ\Fade# < 0.01
				SZ\Fade# = DefaultVolume# * (Float#(SZ\Volume) / 100.0)
			Else
				SZ\Fade# = SZ\Fade# - 0.01
				ChannelVolume(SZ\Channel, SZ\Fade#)
				If SZ\Fade# <= 0.03 Then StopChannel(SZ\Channel) : SZ\Fade# = 0.0
			EndIf
		EndIf
	Next

End Function

; Plays a sound zone sound/music
Function PlaySoundZone(SZ.SoundZone)

	If SZ\SoundID <> 65535
		If SZ\Is3D = True
			SZ\Channel = EmitSound(SZ\LoadedSound, SZ\EN)
		Else
			SZ\Channel = PlaySound(SZ\LoadedSound)
		EndIf
	Else
		SZ\Channel = PlayMusic(SZ\MusicFilename$)
	EndIf
	If SZ\Channel <> 0 Then ChannelVolume(SZ\Channel, DefaultVolume# * (Float#(SZ\Volume) / 100.0))

End Function

; Sets the destination point for an actor instance
Function SetDestination(A.ActorInstance, DestX#, DestZ#, Y#)

	; Do not allow movement into water areas for walking characters
	EType = A\Actor\Environment
	If A\Mount <> Null Then EType = A\Mount\Actor\Environment
	If EType = Environment_Walk
		For W.Water = Each Water
			If Y# < EntityY#(W\EN)
				If Abs(DestX# - EntityX#(W\EN)) < (W\ScaleX# / 2.0)
					If Abs(DestZ# - EntityZ#(W\EN)) < (W\ScaleZ# / 2.0)
						Return
					EndIf
				EndIf
			EndIf
		Next
	EndIf

	; Set destination
	A\DestX# = DestX#
	A\DestZ# = DestZ#
	If A\Mount <> Null
		A\Mount\DestX# = DestX#
		A\Mount\DestZ# = DestZ#
		A\Mount\IsRunning = A\IsRunning
		A\Mount\WalkingBackward = A\WalkingBackward
	EndIf

	; If it is the local player, reset walking backwards and quit progress
	If A = Me
		If Me\Mount <> Null Then Me\Mount\WalkingBackward = False
		A\WalkingBackward = False
		QuitActive = False
	EndIf

End Function

; Encrypts/Decrypts a string (crappily but well enough to negate casual packet sniffing)
Function Encrypt$(S$, Reverse = -1)

	O$ = ""
	For i = 1 To Len(S$)
		O$ = Chr$(Asc(Mid$(S$, i, 1)) + (26 * Reverse)) + O$
	Next
	Return O$

End Function

; Interpolates a variable smoothly between two values
Function CurveValue#(Current#, Destination#, Curve#)

	Return Current# + ((Destination# - Current#) / Curve#)

End Function

; Sorts spells alphabetically
Function SortSpells()

	; Sort known spells
	For i = 0 To 999
		KnownSpellSort(i) = 0
	Next
	For i = 0 To 999
		If Me\SpellLevels[i] > 0
			For j = 0 To 999
				; Free slot in ordered list, fill it
				If KnownSpellSort(j) = 0
					KnownSpellSort(j) = i + 1
					Exit
				; 	
;				ElseIf Me\SpellLevels[KnownSpellSort(j) - 1] < 1
;					KnownSpellSort(j) = i + 1
;					Exit
					
				; Spot taken, insert if this spell is alphabetically previous to the contents
				Else
					Sp.Spell = SpellsList(Me\KnownSpells[i])
					Sp2.Spell = SpellsList(Me\KnownSpells[KnownSpellSort(j) - 1])
					If HighAlphabetical(Sp\Name$, Sp2\Name$)
						For k = 999 To j + 1 Step -1
							KnownSpellSort(k) = KnownSpellSort(k - 1)
						Next
						KnownSpellSort(j) = i + 1
						Exit
					EndIf
				EndIf
			Next
		EndIf
	Next

	; Update GUI
	If SpellsVisible Then UpdateSpellbook()

End Function

; Returns true if A is before B alphabetically
Function HighAlphabetical(A$, B$)

	If Len(A$) = 0 Then Return True
	If Len(B$) = 0 Then Return False

	A$ = Lower$(A$)
	B$ = Lower$(B$)

	Length = Len(B$)
	If Len(A$) > Length Then Length = Len(A$)

	For i = 1 To Length
		If i <= Len(A$) Then CharA$ = Mid$(A$, i, 1) Else CharA$ = " "
		If i <= Len(B$) Then CharB$ = Mid$(B$, i, 1) Else CharB$ = " "
		If Asc(CharA$) < Asc(CharB$) Then Return True
		If Asc(CharA$) > Asc(CharB$) Then Return False
	Next

End Function

; Flashes the screen with a given colour
Function ScreenFlash(R, G, B, TextureID, Length, InitialAlpha# = 1.0)

	If TextureID < 65535
		Tex = GetTexture(TextureID)
		If Tex > 0
			EntityTexture(FlashEN, Tex)
		Else
			RecreateScreenFlashEN()
		EndIf
	Else
		RecreateScreenFlashEN()
	EndIf
	EntityColor(FlashEN, R, G, B)
	EntityAlpha(FlashEN, InitialAlpha#)
	FlashLength = Float#(Length) / InitialAlpha#
	FlashStart = MilliSecs() - (FlashLength - Length)
	Flashing = True

End Function

; Updates the screen flash
Function UpdateScreenFlash()

	Time = MilliSecs() - FlashStart
	If Time >= FlashLength
		Flashing = False
		EntityAlpha(FlashEN, 0.0)
	Else
		EntityAlpha(FlashEN, 1.0 - (Float#(Time) / Float#(FlashLength)))
	EndIf

End Function

; Recreates the screen flash entity to remove the texture
Function RecreateScreenFlashEN()

	If FlashEN > 0 Then FreeEntity(FlashEN)

	FlashEN = GY_CreateQuad(Cam)
	PositionEntity(FlashEN, -10.0, 7.5, 10.0)
	RotateEntity(FlashEN, 0, 0, 0)
	ScaleEntity(FlashEN, 20.0, 15.0, 1.0)
	EntityAlpha(FlashEN, 0.0)
	EntityOrder(FlashEN, -3020)

End Function

; Returns a string describing a certain amount of base money units
Function Money$(Amount)

	If Money4$ <> ""
		Amount4$ = Money4$ + ": " + Str$(Amount / (Money4x * Money3x * Money2x)) + ", "
		Amount = Amount Mod (Money4x * Money3x * Money2x)
	EndIf
	If Money3$ <> ""
		Amount3$ = Money3$ + ": " + Str$(Amount / (Money3x * Money2x)) + ", "
		Amount = Amount Mod (Money3x * Money2x)
	EndIf
	If Money2$ <> ""
		Amount2$ = Money2$ + ": " + Str$(Amount / Money2x) + ", "
		Amount = Amount Mod Money2x
	EndIf
	Amount1$ = Money1$ + ": " + Str$(Amount)
	Return Amount4$ + Amount3$ + Amount2$ + Amount1$

End Function


Function DisableEntity( AI.ActorInstance )
	
	AI\Active = False
End Function

Function EnableEntity( AI.ActorInstance )
	
	AI\Active = True
End Function