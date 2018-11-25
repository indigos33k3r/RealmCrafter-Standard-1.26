;##############################################################################################################################
; Realm Crafter version 1.24																									
; Copyright (C) 2007 Solstar Games, LLC. All rights reserved																	
; contact@solstargames.com																																																		
;																																																																#
; Programmer: Marc 'Dreamora' Schaerer (marc.schaerer@gayasoft.ch), July 2008																								
; Program: Realm Crafter Utility module
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

Global debugBanner.TDebugBanner
Global debugToggleLatency% = 1000 ; milliseconds between toggle interactions



Type TDebugBanner
	Field visible%
	Field fps%, tfps%, lastUpdate%
	Field msToRender%, msTemp%
	Field window%
	Field fpsText%, msRenderText%
	Field vramText%
	Field lastToggle%
End Type


; Initializes the debug banner for usage.
; This is meant to be done before the actor enters the game
Function LoadDebugBanner()
	If debugBanner <> Null Then Return
	
	debugBanner = New TDebugBanner
	debugBanner\window = GY_CreateWindow("Debug Banner", 0.3, 0.0, 0.4, 0.1)
	Local wnd% = debugBanner\window
	debugBanner\visible = False
	
;	debugBanner\fpsText = GY_CreateLabel( wnd, 0.1, 0.1, "FPS: " )
;	debugBanner\msRenderText = GY_CreateLabel( wnd, 0.4, 0.1, "ms per Frame: " )
;	debugBanner\vramText = GY_CreateLabel( wnd, 0.1, 0.55, "VRAM: ")
End Function

; Deinitializes the debug banner for usage.
; This is meant to be done after the actor leaves the game
Function UnLoadDebugBanner()
	If debugBanner = Null Then Return
	
	GY_FreeGadget( debugBanner\window )
	Delete debugBanner
	debugBanner = Null
End Function

; Updates the data shown in the debug banner. not needfully per frame
Function UpdateDebugBanner()
	If debugBanner = Null Then Return
	
	If debugBanner\visible = False
		GY_GadgetAlpha( debugBanner\window, 0.0)
;		GY_GadgetAlpha( debugBanner\fpsText, 0.0)
;		GY_GadgetAlpha( debugBanner\msRenderText, 0.0)
;		GY_GadgetAlpha( debugBanner\vramText, 0.0)
	Else
		GY_GadgetAlpha( debugBanner\window, 0.7)
		
		Local availVid% = AvailVidMem()/1024/1024
		If availVid < 0 Then availVid = 4095 + availVid ; 2048 + ( availVid + 2047 )
		
		Local totalVid% = TotalVidMem()/1024/1024
		If totalVid < 0 Then totalVid = 4095 + totalVid ; 2048 + ( totalVid + 2047 )
		
		If debugBanner\fpsText <> 0
			GY_FreeGadget(debugBanner\fpsText)
			GY_FreeGadget(debugBanner\msRenderText)
			GY_FreeGadget(debugBanner\vramText)
		EndIf
		
		
		Local wnd% = debugBanner\window
		debugBanner\fpsText = GY_CreateLabel( wnd, 0.1, 0.1, "FPS: " + debugBanner\fps )
		debugBanner\msRenderText = GY_CreateLabel( wnd, 0.4, 0.1, "ms per Frame: " + debugBanner\msToRender + "ms" )
		debugBanner\vramText = GY_CreateLabel( wnd, 0.1, 0.55, "VRAM: " + availVid + "MB / " + totalVid + "MB")
		
;		GY_UpdateLabel( debugBanner\fpsText, "FPS: " + debugBanner\fps )
;		GY_UpdateLabel( debugBanner\msRenderText, "ms per Frame: " + debugBanner\msToRender + "ms" )
;		GY_UpdateLabel( debugBanner\vramText, "VRAM: " + availVid + "MB / " + totalVid + "MB")
;		
		GY_GadgetAlpha( debugBanner\fpsText, 0.7)
		GY_GadgetAlpha( debugBanner\msRenderText, 0.7)
		GY_GadgetAlpha( debugBanner\vramText, 0.7)	
	EndIf
End Function

; Updates the FPS calculation. This has to be called per frame
Function UpdateFPS()
	If debugBanner = Null Then Return
	
	debugBanner\tfps=debugBanner\tfps+1
	If MilliSecs() - debugBanner\lastUpdate > 999
		debugBanner\fps = debugBanner\tfps
		debugBanner\tfps = 0
		debugBanner\lastUpdate = MilliSecs()
	EndIf
End Function

; Functionality to decide the amount of milliseconds needed to render the current scene
Function PreRenderWorld()
	debugBanner\msTemp = MilliSecs()
End Function

; Functionality to decide the amount of milliseconds needed to render the current scene
Function PostRenderWorld()
	debugBanner\msTemp = MilliSecs() - debugBanner\msTemp
	debugBanner\msToRender = 0.5 * debugBanner\msTemp + 0.5 * debugBanner\msToRender	; weighted calculation
End Function


Function SetDebugBannerVisibility(flag%)
	If debugBanner = Null Then Return
	
	debugBanner\visible = flag
	If flag = False And debugBanner\fpsText <> 0
		GY_FreeGadget(debugBanner\fpsText)
		GY_FreeGadget(debugBanner\msRenderText)
		GY_FreeGadget(debugBanner\vramText)
	EndIf
	Return
End Function


Function ToggleDebugBannerVisibility()
	If debugBanner = Null Then Return
	If MilliSecs() - debugBanner\lastToggle < debugToggleLatency% Then Return
	
	debugBanner\lastToggle = MilliSecs()
	SetDebugBannerVisibility( 1 - debugBanner\visible )
	Return
End Function
;~IDEal Editor Parameters:
;~C#Blitz3D