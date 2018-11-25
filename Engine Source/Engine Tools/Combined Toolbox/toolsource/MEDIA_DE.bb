; Realm Crafter Media module by Rob W (rottbott@hotmail.com), August 2004

Include "Modules\Encrypt_B3D.bb"

Dim LoadedTextures(65534)
Dim LoadedMeshes(65534)
Dim LoadedMeshScales#(65534)
Dim LoadedMeshX#(65534)
Dim LoadedMeshY#(65534)
Dim LoadedMeshZ#(65534)
Dim LoadedSounds(65534)
Dim TextureFlags(65534)
Global LockedMeshes = 0, LockedTextures = 0, LockedSounds = 0, LockedMusic = 0
Global DefaultVolume# = 1.0

Type LoadedMediaData
	Field ID, DataAddress, Name$, ExtraData
	Field Scale#, X#, Y#, Z#
End Type

; Locks the meshes database (keeps the file open for faster batched Get...() calls)
Function LockMeshes()

	LockedMeshes = OpenFile("Data\Game Data\Meshes.dat")
	Return LockedMeshes

End Function

; Unlocks the meshes database (closes the file again)
Function UnlockMeshes()

	If LockedMeshes <> 0 Then CloseFile LockedMeshes : LockedMeshes = 0 : Return True

End Function

; Locks the textures database (keeps the file open for faster batched Get...() calls)
Function LockTextures()

	LockedTextures = OpenFile("Data\Game Data\Textures.dat")
	Return LockedTextures

End Function

; Unlocks the textures database (closes the file again)
Function UnlockTextures()

	If LockedTextures <> 0 Then CloseFile LockedTextures : LockedTextures = 0 : Return True

End Function

; Locks the sounds database (keeps the file open for faster batched Get...() calls)
Function LockSounds()

	LockedSounds = OpenFile("Data\Game Data\Sounds.dat")
	Return LockedSounds

End Function

; Unlocks the sounds database (closes the file again)
Function UnlockSounds()

	If LockedSounds <> 0 Then CloseFile LockedSounds : LockedSounds = 0 : Return True

End Function

; Locks the music database (keeps the file open for faster batched Get...() calls)
Function LockMusic()

	LockedMusic = OpenFile("Data\Game Data\Music.dat")
	Return LockedMusic

End Function

; Unlocks the sounds database (closes the file again)
Function UnlockMusic()

	If LockedMusic <> 0 Then CloseFile LockedMusic : LockedMusic = 0 : Return True

End Function

; Creates a new (blank) media database
Function CreateDatabase(Filename$)

	F = WriteFile(Filename$)
	If F = 0 Then Return False

	For ID = 0 To 65534
		WriteInt F, 0
	Next

	CloseFile F
	Return True

End Function

; Removes a mesh from the database (slow)
Function RemoveMeshFromDatabase(ID)

	UnloadMesh(ID)

	; Open index file
	If LockedMeshes = 0
		F = OpenFile("Data\Game Data\Meshes.dat")
		If F = 0 Then Return False
	Else
		F = LockedMeshes
	EndIf

	; Read in the data address for every ID except this one
	For i = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress > 0 And i <> ID
			L.LoadedMediaData = New LoadedMediaData
			L\ID = i
			L\DataAddress = DataAddress
		EndIf
	Next

	; Read in the actual data for each existing ID
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\DataAddress
		L\ExtraData = ReadByte(F)
		L\Scale# = ReadFloat#(F)
		L\X# = ReadFloat#(F)
		L\Y# = ReadFloat#(F)
		L\Z# = ReadFloat#(F)
		L\Name$ = ReadString$(F)
	Next

	; Clear the database
	CloseFile F
	Result = CreateDatabase("Data\Game Data\Meshes.dat")
	If Result = False Then LockedMeshes = 0 : Return False

	; Reopen it
	F = OpenFile("Data\Game Data\Meshes.dat")
	If LockedMeshes <> 0 Then LockedMeshes = F

	; Write everything back out again
	FileEnd = 65535 * 4
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\ID * 4
		WriteInt F, FileEnd
		SeekFile F, FileEnd
		WriteByte F, L\ExtraData
		WriteFloat F, L\Scale#
		WriteFloat F, L\X#
		WriteFloat F, L\Y#
		WriteFloat F, L\Z#
		WriteString F, L\Name$
		FileEnd = FilePos(F)
	Next
	Delete Each LoadedMediaData

	If LockedMeshes = 0 Then CloseFile F
	Return True

End Function

; Removes a texture from the database (slow)
Function RemoveTextureFromDatabase(ID)

	UnloadTexture(ID)

	; Open index file
	If LockedTextures = 0
		F = OpenFile("Data\Game Data\Textures.dat")
		If F = 0 Then Return False
	Else
		F = LockedTextures
	EndIf

	; Read in the data address for every ID except this one
	For i = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress > 0 And i <> ID
			L.LoadedMediaData = New LoadedMediaData
			L\ID = i
			L\DataAddress = DataAddress
		EndIf
	Next

	; Read in the actual data for each existing ID
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\DataAddress
		L\ExtraData = ReadShort(F)
		L\Name$ = ReadString$(F)
	Next

	; Clear the database
	CloseFile F
	Result = CreateDatabase("Data\Game Data\Textures.dat")
	If Result = False Then LockedTextures = 0 : Return False

	; Reopen it
	F = OpenFile("Data\Game Data\Textures.dat")
	If LockedTextures <> 0 Then LockedTextures = F

	; Write everything back out again
	FileEnd = 65535 * 4
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\ID * 4
		WriteInt F, FileEnd
		SeekFile F, FileEnd
		WriteShort F, L\ExtraData
		WriteString F, L\Name$
		FileEnd = FilePos(F)
	Next
	Delete Each LoadedMediaData

	If LockedTextures = 0 Then CloseFile F
	Return True

End Function

; Removes a sound from the database (slow)
Function RemoveSoundFromDatabase(ID)

	UnloadSound(ID)

	; Open index file
	If LockedSounds = 0
		F = OpenFile("Data\Game Data\Sounds.dat")
		If F = 0 Then Return False
	Else
		F = LockedSounds
	EndIf

	; Read in the data address for every ID except this one
	For i = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress > 0 And i <> ID
			L.LoadedMediaData = New LoadedMediaData
			L\ID = i
			L\DataAddress = DataAddress
		EndIf
	Next

	; Read in the actual data for each existing ID
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\DataAddress
		L\ExtraData = ReadByte(F)
		L\Name$ = ReadString$(F)
	Next

	; Clear the database
	CloseFile F
	Result = CreateDatabase("Data\Game Data\Sounds.dat")
	If Result = False Then LockedSounds = 0 : Return False

	; Reopen it
	F = OpenFile("Data\Game Data\Sounds.dat")
	If LockedSounds <> 0 Then LockedSounds = F

	; Write everything back out again
	FileEnd = 65535 * 4
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\ID * 4
		WriteInt F, FileEnd
		SeekFile F, FileEnd
		WriteByte F, L\ExtraData
		WriteString F, L\Name$
		FileEnd = FilePos(F)
	Next
	Delete Each LoadedMediaData

	If LockedSounds = 0 Then CloseFile F
	Return True

End Function

; Removes music from the database (slow)
Function RemoveMusicFromDatabase(ID)

	; Open index file
	If LockedMusic = 0
		F = OpenFile("Data\Game Data\Music.dat")
		If F = 0 Then Return False
	Else
		F = LockedMusic
	EndIf

	; Read in the data address for every ID except this one
	For i = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress > 0 And i <> ID
			L.LoadedMediaData = New LoadedMediaData
			L\ID = i
			L\DataAddress = DataAddress
		EndIf
	Next

	; Read in the actual data for each existing ID
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\DataAddress
		L\Name$ = ReadString$(F)
	Next

	; Clear the database
	CloseFile F
	Result = CreateDatabase("Data\Game Data\Music.dat")
	If Result = False Then LockedMusic = 0 : Return False

	; Reopen it
	F = OpenFile("Data\Game Data\Music.dat")
	If LockedMusic <> 0 Then LockedMusic = F

	; Write everything back out again
	FileEnd = 65535 * 4
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\ID * 4
		WriteInt F, FileEnd
		SeekFile F, FileEnd
		WriteString F, L\Name$
		FileEnd = FilePos(F)
	Next
	Delete Each LoadedMediaData

	If LockedMusic = 0 Then CloseFile F
	Return True

End Function

; Adds a new mesh to the database and returns its ID
Function AddMeshToDatabase(FileName$, IsAnim,encryptf=1)
	; Open index file
	If LockedMeshes = 0
		F = OpenFile("Data\Game Data\Meshes.dat")
		If F = 0 Then Return -1
	Else
		F = LockedMeshes
	EndIf

	; Check every mesh to make sure this one isn't already there
	SeekFile F, (65535 * 4)
	While Eof(F) = False
		Pos = FilePos(F)
		MIsAnim = ReadByte(F)
		ReadFloat#(F)
		ReadFloat#(F)
		ReadFloat#(F)
		ReadFloat#(F)
		Name$ = ReadString$(F)
		; If this mesh is already in the file, return error
		If (Upper$(Name$) = Upper$(Filename$)) And (MIsAnim = IsAnim)
			If LockedMeshes = 0 Then CloseFile F
			Return -1
		EndIf
	Wend

	; Find the first free ID
	SeekFile F, 0
	For ID = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress = 0
			; Write mesh data to index file
			SeekFile F, ID * 4
			WriteInt F, FileSize("Data\Game Data\Meshes.dat")
			SeekFile F, FileSize("Data\Game Data\Meshes.dat")
			WriteByte F, IsAnim
			WriteFloat F, 1.0
			WriteFloat F, 0.0
			WriteFloat F, 0.0
			WriteFloat F, 0.0
			WriteString F, Filename$
			If LockedMeshes = 0 Then CloseFile F
			; Encrypt .b3d files
			If Len(Filename$) > 4
				If Upper$(Right$(FileName$, 4)) = ".B3D" And encryptf=1 Then 
				Encrypt_B3D("Data\Meshes\" + FileName$)
                DebugLog "ENCRYPTED!"
                EndIf 
			EndIf
			; Return new ID
			Return ID
		EndIf
	Next

	; No free ID found
	If LockedMeshes = 0 Then CloseFile F
	Return -1

End Function

; Adds a new texture to the database and returns its ID
Function AddTextureToDatabase(Filename$, Flags)

	; Open index file
	If LockedTextures = 0
		F = OpenFile("Data\Game Data\Textures.dat")
		If F = 0 Then Return -1
	Else
		F = LockedTextures
	EndIf

	; Check every texture to make sure this one isn't already there
	SeekFile F, (65535 * 4)
	While Eof(F) = False
		Pos = FilePos(F)
		TFlags = ReadShort(F)
		Name$ = ReadString$(F)
		; If this texture is already in the file, return the existing ID
		If (Upper$(Name$) = Upper$(Filename$)) And (TFlags = Flags)
			If LockedTextures = 0 Then CloseFile F
			Return -1
		EndIf
	Wend

	; Find the first free ID
	SeekFile F, 0
	For ID = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress = 0
			; Write texture data to index file
			SeekFile F, ID * 4
			WriteInt F, FileSize("Data\Game Data\Textures.dat")
			SeekFile F, FileSize("Data\Game Data\Textures.dat")
			WriteShort F, Flags
			WriteString F, Filename$
			If LockedTextures = 0 Then CloseFile F
			; Return new ID
			Return ID
		EndIf
	Next

	; No free ID found
	If LockedTextures = 0 Then CloseFile F
	Return -1

End Function

; Adds a new sound to the database and returns its ID
Function AddSoundToDatabase(Filename$, Is3D)

	; Open index file
	If LockedSounds = 0
		F = OpenFile("Data\Game Data\Sounds.dat")
		If F = 0 Then Return -1
	Else
		F = LockedSounds
	EndIf

	; Check every sound to make sure this one isn't already there
	SeekFile F, (65535 * 4)
	While Eof(F) = False
		Pos = FilePos(F)
		SIs3D = ReadByte(F)
		Name$ = ReadString$(F)
		; If this sound is already in the file, return the existing ID
		If (Upper$(Name$) = Upper$(Filename$)) And (SIs3D = Is3D)
			If LockedSounds = 0 Then CloseFile F
			Return -1
		EndIf
	Wend

	; Find the first free ID
	SeekFile F, 0
	For ID = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress = 0
			; Write sound data to index file
			SeekFile F, ID * 4
			WriteInt F, FileSize("Data\Game Data\Sounds.dat")
			SeekFile F, FileSize("Data\Game Data\Sounds.dat")
			WriteByte F, Is3D
			WriteString F, Filename$
			If LockedSounds = 0 Then CloseFile F
			; Return new ID
			Return ID
		EndIf
	Next

	; No free ID found
	If LockedSounds = 0 Then CloseFile F
	Return -1

End Function

; Adds new music to the database and returns its ID
Function AddMusicToDatabase(Filename$)

	; Open index file
	If LockedMusic = 0
		F = OpenFile("Data\Game Data\Music.dat")
		If F = 0 Then Return -1
	Else
		F = LockedMusic
	EndIf

	; Check all music to make sure this one isn't already there
	SeekFile F, (65535 * 4)
	While Eof(F) = False
		Pos = FilePos(F)
		Name$ = ReadString$(F)
		; If this music is already in the file, return the existing ID
		If Upper$(Name$) = Upper$(Filename$)
			If LockedMusic = 0 Then CloseFile F
			Return -1
		EndIf
	Wend

	; Find the first free ID
	SeekFile F, 0
	For ID = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress = 0
			; Write sound data to index file
			SeekFile F, ID * 4
			WriteInt F, FileSize("Data\Game Data\Music.dat")
			SeekFile F, FileSize("Data\Game Data\Music.dat")
			WriteString F, Filename$
			; Return new ID
			If LockedMusic = 0 Then CloseFile F
			Return ID
		EndIf
	Next

	; No free ID found
	If LockedMusic = 0 Then CloseFile F
	Return -1

End Function

; Gets the name and animation byte for a given mesh
Function GetMeshName$(ID)

	; Open index file
	If LockedMeshes = 0
		F = OpenFile("Data\Game Data\Meshes.dat")
		If F = 0 Then Return ""
	Else
		F = LockedMeshes
	EndIf

	; Find data address in file index
	SeekFile F, ID * 4
	DataAddress = ReadInt(F)
	If DataAddress = 0
		If LockedMeshes = 0 Then CloseFile F
		Return ""
	EndIf
	; Read in mesh data
	SeekFile F, DataAddress
	IsAnim = ReadByte(F)
	ReadFloat#(F)
	ReadFloat#(F)
	ReadFloat#(F)
	ReadFloat#(F)
	Name$ = ReadString$(F)

	If LockedMeshes = 0 Then CloseFile F

	Return Name$ + Chr$(IsAnim)

End Function

; Gets the name and flags for a given texture
Function GetTextureName$(ID)

	; Open index file
	If LockedTextures = 0
		F = OpenFile("Data\Game Data\Textures.dat")
		If F = 0 Then Return ""
	Else
		F = LockedTextures
	EndIf

	; Find data address in file index
	SeekFile F, ID * 4
	DataAddress = ReadInt(F)
	If DataAddress = 0
		If LockedTextures = 0 Then CloseFile F
		Return ""
	EndIf
	; Read in texture data
	SeekFile F, DataAddress
	Flags = ReadShort(F)
	Name$ = ReadString$(F)

	If LockedTextures = 0 Then CloseFile F

	Return Name$ + Chr$(Flags)

End Function

; Gets the name and 3D byte for a given sound
Function GetSoundName$(ID)

	; Open index file
	If LockedSounds = 0
		F = OpenFile("Data\Game Data\Sounds.dat")
		If F = 0 Then Return ""
	Else
		F = LockedSounds
	EndIf

	; Find data address in file index
	SeekFile F, ID * 4
	DataAddress = ReadInt(F)
	If DataAddress = 0
		If LockedSounds = 0 Then CloseFile F
		Return ""
	EndIf
	; Read in sound data
	SeekFile F, DataAddress
	Is3D = ReadByte(F)
	Name$ = ReadString$(F)

	If LockedSounds = 0 Then CloseFile F

	Return Name$ + Chr$(Is3D)

End Function

; Gets the name of a given piece of music
Function GetMusicName$(ID)

	; Open index file
	If LockedMusic = 0
		F = OpenFile("Data\Game Data\Music.dat")
		If F = 0 Then Return ""
	Else
		F = LockedMusic
	EndIf

	; Find data address in file index
	SeekFile F, ID * 4
	DataAddress = ReadInt(F)
	If DataAddress = 0
		If LockedMusic = 0 Then CloseFile F
		Return ""
	EndIf
	; Read in sound data
	SeekFile F, DataAddress
	Name$ = ReadString$(F)

	If LockedMusic = 0 Then CloseFile F

	Return Name$

End Function

; Changes the scale for a mesh
Function SetMeshScale(ID, Scale#)

	LoadedMeshScales#(ID) = Scale#

	; Open index file
	If LockedMeshes = 0
		F = OpenFile("Data\Game Data\Meshes.dat")
		If F = 0 Then Return False
	Else
		F = LockedMeshes
	EndIf

	; Find data address in file index
	SeekFile F, ID * 4
	DataAddress = ReadInt(F)
	If DataAddress = 0
		If LockedMeshes = 0 Then CloseFile F
		Return False
	EndIf
	; Write new scale float
	SeekFile F, DataAddress + 1
	WriteFloat F, Scale#

	If LockedMeshes = 0 Then CloseFile F

	Return True

End Function

; Changes the offset for a mesh
Function SetMeshOffset(ID, X#, Y#, Z#)

	LoadedMeshX#(ID) = X#
	LoadedMeshY#(ID) = Y#
	LoadedMeshZ#(ID) = Z#

	; Open index file
	If LockedMeshes = 0
		F = OpenFile("Data\Game Data\Meshes.dat")
		If F = 0 Then Return False
	Else
		F = LockedMeshes
	EndIf

	; Find data address in file index
	SeekFile F, ID * 4
	DataAddress = ReadInt(F)
	If DataAddress = 0
		If LockedMeshes = 0 Then CloseFile F
		Return False
	EndIf
	; Write new scale float
	SeekFile F, DataAddress + 5
	WriteFloat F, X#
	WriteFloat F, Y#
	WriteFloat F, Z#

	If LockedMeshes = 0 Then CloseFile F

	Return True

End Function

; Gets the handle for a given mesh (this will load it if it isn't present)
Function GetMesh(ID,decryptf=1)


		; Read in filename and other data from index file
		If LockedMeshes = 0
			F = OpenFile("Data\Game Data\Meshes.dat")
			If F = 0 Then Return ""
		Else
			F = LockedMeshes
		EndIf

		; Find data address in file index
		SeekFile F, ID * 4
		DataAddress = ReadInt(F)
		If DataAddress = 0
			If LockedMeshes = 0 Then CloseFile F
			Return 0
		EndIf
		; Read in mesh data
		SeekFile F, DataAddress
		IsAnim = ReadByte(F)
		LoadedMeshScales#(ID) = ReadFloat#(F)
		LoadedMeshX#(ID) = ReadFloat#(F)
		LoadedMeshY#(ID) = ReadFloat#(F)
		LoadedMeshZ#(ID) = ReadFloat#(F)
		Name$ = ReadString$(F)

		If LockedMeshes = 0 Then CloseFile F

		; Load the mesh
		If IsAnim = True
			temp = LoadAnimMesh("Data\Meshes\" + Name$)
			If temp = 0 Then Return 0
			;HideEntity temp
			ScaleEntity temp, LoadedMeshScales#(ID), LoadedMeshScales#(ID), LoadedMeshScales#(ID)
			If Len(Name$) > 4
				If Upper$(Right$(Name$, 4)) = ".B3D" And decryptf=1  Then Decrypt_B3D(temp)
			EndIf
	        Return temp             
		Else
			temp = LoadMesh("Data\Meshes\" + Name$)
			If temp = 0 Then Return 0
			;HideEntity temp
			ScaleEntity temp, LoadedMeshScales#(ID), LoadedMeshScales#(ID), LoadedMeshScales#(ID)
			If Len(Name$) > 4
				If Upper$(Right$(Name$, 4)) = ".B3D" And decryptf=1 Then Decrypt_B3D(temp)
			EndIf
	        Return temp
		EndIf


	Return temp

End Function

; Gets the handle for a given texture (this will load it if it isn't present)
Function GetTexture(ID, Copy = False)

	If LoadedTextures(ID) = 0

		; Read in filename and other data from index file
		If LockedTextures = 0
			F = OpenFile("Data\Game Data\Textures.dat")
			If F = 0 Then Return -1
		Else
			F = LockedTextures
		EndIf

		; Find data address in file index
		SeekFile F, ID * 4
		DataAddress = ReadInt(F)
		If DataAddress = 0
			If LockedTextures = 0 Then CloseFile F
			Return 0
		EndIf
		; Read in texture data
		SeekFile F, DataAddress
		Flags = ReadShort(F)
		Name$ = ReadString$(F)

		If LockedTextures = 0 Then CloseFile F

		LoadedTextures(ID) = LoadTexture("Data\Textures\" + Name$, Flags)
		TextureFlags(ID) = Flags

	EndIf
	If Copy = True
		If LoadedTextures(ID) = 0 Then Return 0
		Return CopyTexture(LoadedTextures(ID), TextureFlags(ID))
	Else
		Return LoadedTextures(ID)
	EndIf

End Function

; Gets the handle for a given sound (this will load it if it isn't present)
Function GetSound(ID)

	If ID < 0 Or ID > 65534 Then Return 0

	If LoadedSounds(ID) = 0

		; Read in filename and other data from index file
		If LockedSounds = 0
			F = OpenFile("Data\Game Data\Sounds.dat")
			If F = 0 Then Return -1
		Else
			F = LockedSounds
		EndIf

		; Find data address in file index
		SeekFile F, ID * 4
		DataAddress = ReadInt(F)
		If DataAddress = 0
			If LockedSounds = 0 Then CloseFile F
			Return 0
		EndIf
		; Read in sound data
		SeekFile F, DataAddress
		Is3D = ReadByte(F)
		Name$ = ReadString$(F)

		If LockedSounds = 0 Then CloseFile F

		If Is3D = True
			LoadedSounds(ID) = Load3DSound("Data\Sounds\" + Name$)
			SoundVolume LoadedSounds(ID), DefaultVolume#
		Else
			LoadedSounds(ID) = LoadSound("Data\Sounds\" + Name$)
			SoundVolume LoadedSounds(ID), DefaultVolume#
		EndIf

	EndIf
	Return LoadedSounds(ID)

End Function

; Unloads a mesh
Function UnloadMesh(ID)

	If LoadedMeshes(ID) <> 0 Then FreeEntity LoadedMeshes(ID)
	LoadedMeshes(ID) = 0

End Function

; Unloads a texture
Function UnloadTexture(ID)

	If LoadedTextures(ID) <> 0 Then FreeTexture LoadedTextures(ID)
	LoadedTextures(ID) = 0

End Function

; Unloads a sound
Function UnloadSound(ID)

	If LoadedSounds(ID) <> 0 Then FreeSound LoadedSounds(ID)
	LoadedSounds(ID) = 0

End Function

; Scales a mesh entity to be a certain size without altering the mesh (works on animated meshes)
Function SizeEntity(EN, Width#, Height#, Depth#, Uniform = False)

	; Find mesh edges
	Result.MeshMinMaxVertices = MeshMinMaxVertices(EN)
	MWidth#  = Result\MaxX# - Result\MinX#
	MHeight# = Result\MaxY# - Result\MinY#
	MDepth#  = Result\MaxZ# - Result\MinZ#
	Delete Result

	; Scale
	If Uniform = False
		ScaleEntity EN, Width# / MWidth#, Height# / MHeight#, Depth# / MDepth#
	Else
		XScale# = Width# / MWidth#
		YScale# = Height# / MHeight#
		ZScale# = Depth# / MDepth#

		If YScale# < XScale# Then XScale# = YScale#
		If ZScale# < XScale# Then XScale# = ZScale#

		ScaleEntity EN, XScale#, XScale#, XScale#
	EndIf

End Function

; Copies a texture in memory
Function CopyTexture(Tex, Flags)

	NewTex = CreateTexture(TextureWidth(Tex), TextureHeight(Tex), Flags)

	OldBuffer = GraphicsBuffer()
	SetBuffer(TextureBuffer(NewTex))
		LockBuffer()
		LockBuffer(TextureBuffer(Tex))
			For x = 0 To TextureWidth(Tex) - 1
			For y = 0 To TextureHeight(Tex) - 1
				CopyPixelFast(x, y, TextureBuffer(Tex), x, y)
			Next
			Next
		UnlockBuffer(TextureBuffer(Tex))
		UnlockBuffer()
	SetBuffer(OldBuffer)

	Return NewTex

End Function

; Recursively retrives the min/max vertex positions of a mesh or heirarchy of meshes
Type MeshMinMaxVertices
	Field MinX#, MaxX#
	Field MinY#, MaxY#
	Field MinZ#, MaxZ#
End Type
Function MeshMinMaxVertices.MeshMinMaxVertices(EN)

	Result.MeshMinMaxVertices = New MeshMinMaxVertices
	If Upper$(EntityClass$(EN)) = "MESH"
		For i = 1 To CountSurfaces(EN)
			Surf = GetSurface(EN, i)
			For j = 0 To CountVertices(Surf) - 1
				X# = VertexX#(Surf, j)
				Y# = VertexY#(Surf, j)
				Z# = VertexZ#(Surf, j)
				If X# < Result\MinX# Then Result\MinX# = X# ElseIf X# > Result\MaxX# Then Result\MaxX# = X#
				If Y# < Result\MinY# Then Result\MinY# = Y# ElseIf Y# > Result\MaxY# Then Result\MaxY# = Y#
				If Z# < Result\MinZ# Then Result\MinZ# = Z# ElseIf Z# > Result\MaxZ# Then Result\MaxZ# = Z#
			Next
		Next
	EndIf
	For i = 1 To CountChildren(EN)
		ChildResult.MeshMinMaxVertices = MeshMinMaxVertices(GetChild(EN, i))
		If ChildResult\MinX# < Result\MinX# Then Result\MinX# = ChildResult\MinX#
		If ChildResult\MinY# < Result\MinY# Then Result\MinY# = ChildResult\MinY#
		If ChildResult\MinZ# < Result\MinZ# Then Result\MinZ# = ChildResult\MinZ#
		If ChildResult\MaxX# > Result\MaxX# Then Result\MaxX# = ChildResult\MaxX#
		If ChildResult\MaxY# > Result\MaxY# Then Result\MaxY# = ChildResult\MaxY#
		If ChildResult\MaxZ# > Result\MaxZ# Then Result\MaxZ# = ChildResult\MaxZ#
		Delete ChildResult
	Next
	Return Result

End Function