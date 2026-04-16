package org.sharkk2.shrkengine.engine;

import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.sharkk2.shrkengine.engine.helpers.Utils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.openal.AL11.*;
import static org.lwjgl.openal.ALC11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class AudioManager {
   private final Engine engine;
   private final long device;
   private final long context;
   private final Map<String, Utils.WavData> audioRegistry = new HashMap<>();
   private final Map<String, Integer> audioBuffers = new HashMap<>();
   private final List<Integer> activeSources = new ArrayList<>();



    public record Audio(
           String audioID,
           Vector3f position, Vector3f direction, Vector3f velocity,
           boolean global, boolean looping,
           float pitch, float maxDistance, float volume
   ) {}


   public AudioManager(Engine engine) {
       this.engine = engine;
       device = alcOpenDevice((ByteBuffer) null);
       if (device == NULL) throw new RuntimeException("Failed to open OpenAL device");

       context = alcCreateContext(device, (int[]) null);
       if (context == NULL) throw new RuntimeException("Failed to create OpenAL context");
       alcMakeContextCurrent(context);
       AL.createCapabilities(ALC.createCapabilities(device));
       alDistanceModel(AL_LINEAR_DISTANCE_CLAMPED);

   }

   public void updateAudio() {
       Camera camera = engine.getCamera();
       Vector3f camPos = camera.getPosition();
       Vector3f velocity = camera.getVelocity();
       Vector3f front = camera.getDirection();
       Vector3f up = camera.getUp();

       alListener3f(AL_POSITION, camPos.x, camPos.y, camPos.z);
       FloatBuffer orientation = BufferUtils.createFloatBuffer(6)
               .put(front.x).put(front.y).put(front.z)
               .put(up.x).put(up.y).put(up.z);
       orientation.flip();
       alListenerfv(AL_ORIENTATION, orientation);
       alListener3f(AL_VELOCITY, velocity.x, velocity.y, velocity.z);


   }

   public void registerAudio(String key, String path) {
       if (audioRegistry.containsKey(key)) return;
       try {
           Utils.WavData sound = Utils.loadWav(path, true);
           audioRegistry.put(key, sound);
           int bufferid = alGenBuffers();
           alBufferData(bufferid, sound.format(), sound.buffer(), (int)sound.fmt().getSampleRate());
           audioBuffers.put(key, bufferid);
       } catch (Exception e) {System.err.println("[AudioManager] " + e.getMessage());}
   }

   public int playAudio(Audio audio) {
       if (!audioRegistry.containsKey(audio.audioID())) return -1;
       if (!audioBuffers.containsKey(audio.audioID())) return -1;
       int sourceId = alGenSources();
       alSourcei(sourceId, AL_BUFFER, audioBuffers.get(audio.audioID()));
       alSource3f(sourceId, AL_POSITION, audio.position.x, audio.position.y, audio.position.z);
       alSource3f(sourceId, AL_VELOCITY, audio.velocity.x, audio.velocity.y, audio.velocity.z);
       alSourcef(sourceId, AL_GAIN, audio.volume());
       alSourcef(sourceId, AL_PITCH, audio.pitch());
       alSource3f(sourceId, AL_DIRECTION, audio.direction.x, audio.direction.y, audio.direction.z);
       alSourcei(sourceId, AL_LOOPING, audio.looping() ? 1:0);
       if (audio.global()) {
           alSourcei(sourceId, AL_SOURCE_RELATIVE, AL_TRUE);
           alSource3f(sourceId, AL_POSITION, 0f, 0f, 0f);
       }
       alSourcef(sourceId, AL_MAX_DISTANCE, audio.maxDistance());
       alSourcef(sourceId, AL_REFERENCE_DISTANCE, 1f);


       alSourcePlay(sourceId);
       activeSources.add(sourceId);
       return sourceId;
   }

    public void cleanupSources() {
        for (int sourceId : activeSources) {
            alSourceStop(sourceId);
            alDeleteSources(sourceId);
        }
        activeSources.clear();
    }

    public void stopAudio(int sourceID) {
        activeSources.removeIf(s -> s == sourceID);
        alSourceStop(sourceID);
        alDeleteSources(sourceID);
    }

   public void cleanup() {
       cleanupSources();
       audioRegistry.clear();
       for (int bufferId : audioBuffers.values()) {
           alDeleteBuffers(bufferId);
       }
       audioBuffers.clear();
       alcMakeContextCurrent(NULL);
       alcDestroyContext(context);
       alcCloseDevice(device);
   }
}
