/*
 * Copyright (c) 2013 - 2016 Stefan Muller Arisona, Simon Schubiger
 * Copyright (c) 2013 - 2016 FHNW & ETH Zurich
 * All rights reserved.
 *
 * Contributions by: Filip Schramka, Samuel von Stachelski
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *  Neither the name of FHNW / ETH Zurich nor the names of its contributors may
 *   be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package ch.fhnw.ether.platform;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import ch.fhnw.ether.render.gl.GLContextManager;
import ch.fhnw.ether.view.IWindow;
import ch.fhnw.util.math.Vec2;

/**
 * GLFW window class.
 *
 * @author radar
 */
final class GLFWWindow implements IWindow {
	private static final boolean DBG = false;
	
	private static final AtomicInteger NUM_WINDOWS = new AtomicInteger();
	
	// for getting window/framebuffer sizes. to be used on main thread only
	private static final int[] INT_ARRAY_0 = new int[1];
	private static final int[] INT_ARRAY_1 = new int[1];
	
	private String title;
	private boolean visible;
	private Vec2 windowPosition = Vec2.ZERO;
	private Vec2 windowSize = Vec2.ONE;
	private Vec2 framebufferSize = Vec2.ONE;
	
	private long window;
	
	private Vec2 pointerPosition = Vec2.ZERO;
	private int pointerButtons = 0;
	private boolean pointerDragged = false;
	private int modifiers = 0;
	private int vao = -1;
	
	private IWindowListener windowListener;
	private IKeyListener keyListener;
	private IPointerListener pointerListener;
	
	private final Lock contextLock = new ReentrantLock();
	private final IContext context = new IContext() {
		@Override
		public void close() throws Exception {
			releaseContext();
		}
	};
	
	/**
	 * Creates window.
	 *
	 * @param size
	 *            the requested size
	 * @param title
	 *            the windows's title
	 * @param decorated
	 *            true for a decorated window
	 */
	// TODO: add more elaborate window configuration options
	public GLFWWindow(Vec2 size, String title, boolean decorated) {
		checkMainThread();
		
		if (DBG)
			System.out.println("window create: " + size + " " + title);

		if (title != null)
			NUM_WINDOWS.incrementAndGet();
		else
			title = "context";
		
		this.title = title;
		this.visible = false;
		this.windowSize = size;
		
		// make sure this comes before setting up window hints due to side effects!
        GLFWWindow shared = (GLFWWindow)GLContextManager.getSharedContextWindow();

        GLFW.glfwDefaultWindowHints();

        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);		
        
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, decorated ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, decorated ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);

        window = GLFW.glfwCreateWindow((int)size.x, (int)size.y, title, MemoryUtil.NULL, shared != null ? shared.window : MemoryUtil.NULL);
        if (window == MemoryUtil.NULL)
            throw new RuntimeException("failed to create window");
        
        setCallbacks();
        
        GLFW.glfwGetFramebufferSize(window, INT_ARRAY_0, INT_ARRAY_1);
        framebufferSize = new Vec2(INT_ARRAY_0[0], INT_ARRAY_1[0]);
		
		try (IContext context = acquireContext()) {
			GLFW.glfwSwapInterval(1);
		} catch (Exception e) {
		}
		
		if (DBG)
			System.out.println("window created.");		
 	}
	
	@Override
	public void destroy() {
		checkMainThread();
		
		if (DBG)
			System.out.println("window destroy: " + title);
		
		// note: we cannot destroy window as long as the context is acquired (i.e. window is rendering)
		contextLock.lock();
		Callbacks.glfwFreeCallbacks(window);
		GLFW.glfwDestroyWindow(window);
		window = 0;
		contextLock.unlock();
		
		// XXX not sure if this is what we want in all cases, but for now ok.
		if (NUM_WINDOWS.decrementAndGet() == 0)
			Platform.get().exit();
	}
	
	@Override
	public boolean isDestroyed() {
		return window == 0;
	}
	
	@Override
	public IContext acquireContext() {
		contextLock.lock();
		GLFW.glfwMakeContextCurrent(window);
		try {
			GL.getCapabilities();
		} catch (Exception e) {
			GL.createCapabilities(true);			
		}
		// we're not using VAOs but still need to create one
		if (vao == -1)
			vao = GL30.glGenVertexArrays();
		GL30.glBindVertexArray(vao);
		return context;
	}
	
	@Override
	public void releaseContext() {
		GLFW.glfwMakeContextCurrent(0);
		contextLock.unlock();
	}
	
	@Override
	public void swapBuffers() {
		if (window == 0)
			return;
		GLFW.glfwSwapBuffers(window);
	}

	@Override
	public String getTitle() {
		return title;
	}
	
	@Override
	public void setTitle(String title) {
		checkMainThread();
		this.title = title;
		GLFW.glfwSetWindowTitle(window, title);
	}

	@Override
	public boolean isVisible() {
		return visible;
	}
	
	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
		if (visible) {
			GLFW.glfwShowWindow(window);
		} else {
			GLFW.glfwHideWindow(window);
		}
	}
	
	@Override
	public Vec2 getPosition() {
		return windowPosition;
	}

	@Override
	public void setPosition(Vec2 position) {
		checkMainThread();
		windowPosition = position;
		GLFW.glfwSetWindowPos(window, (int)position.x, (int)position.y);
	}
	
	@Override
	public Vec2 getSize() {
		return windowSize;
	}

	@Override
	public void setSize(Vec2 size) {
		checkMainThread();
		windowSize = size;
		GLFW.glfwSetWindowSize(window, (int)size.x, (int)size.y);
	}
	
	@Override
	public Vec2 getFramebufferSize() {
		return framebufferSize;
	}
	
	@Override
	public void setFullscreen(boolean enabled) {
		checkMainThread();
	}

	@Override
	public void setPointerMode(PointerMode mode) {
		checkMainThread();
		switch (mode) {
		case NORMAL:
			GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
			break;
		case DISABLED:
			GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
			break;
		case HIDDEN:
			GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
			break;
		}
	}

	@Override
	public void setPointerPosition(float x, float y) {
		checkMainThread();
		GLFW.glfwSetCursorPos(window, x, y);
	}
	
	@Override
	public void setWindowListener(IWindowListener windowListener) {
		checkMainThread();
		this.windowListener = windowListener;
	}
	
	@Override
	public void setKeyListener(IKeyListener keyListener) {
		checkMainThread();
		this.keyListener = keyListener;
	}
	
	@Override
	public void setPointerListener(IPointerListener pointerListener) {
		checkMainThread();
		this.pointerListener = pointerListener;
	}
	
	private void setCallbacks() {
		//---- WINDOW CALLBACKS

		GLFW.glfwSetWindowCloseCallback(window, w -> {
			if (DBG)
				System.out.println("window close request: " + title);

			// still handle close even if there's no listener
			if (windowListener == null) {
				Platform.get().runOnMainThread(() -> {
					destroy();
				});
				return;
			}
			windowListener.windowCloseRequest(GLFWWindow.this);
		});

		GLFW.glfwSetWindowRefreshCallback(window, w -> {
			if (DBG)
				System.out.println("window refresh request: " + title);

			if (windowListener == null)
				return;
			windowListener.windowRefresh(GLFWWindow.this);
		});

		GLFW.glfwSetWindowFocusCallback(window, (w, focused) -> {
			if (DBG)
				System.out.println("window focus: " + title + " " + focused);

			if (windowListener == null)
				return;
			windowListener.windowFocusChanged(GLFWWindow.this, focused);
		});

		GLFW.glfwSetWindowPosCallback(window, (w, xpos, ypos) -> {
			if (DBG)
				System.out.println("window position request: " + title);

			windowPosition = new Vec2(xpos, ypos);
		});

		GLFW.glfwSetWindowSizeCallback(window, (w, width, height) -> {
			if (DBG)
				System.out.println("window resize: " + title + " " + width + " " + height);

			// note: we're currently not using this callback, since window
			// size is explicitly fetched in framebuffer callback.
		});

		GLFW.glfwSetFramebufferSizeCallback(window, (w, width, height) -> {
            if (DBG)
                System.out.println("framebuffer resize: " + title + " " + width + " " + height);

            GLFW.glfwGetWindowSize(w, INT_ARRAY_0, INT_ARRAY_1);
            windowSize = new Vec2(INT_ARRAY_0[0], INT_ARRAY_1[0]);
            framebufferSize = new Vec2(width, height);

            if (windowListener == null)
                return;
            windowListener.windowResized(GLFWWindow.this, windowSize, framebufferSize);
        });

		GLFW.glfwSetWindowIconifyCallback(window, (window1, iconified) -> {
            if (DBG)
                System.out.println("window iconified: " + iconified);

            visible = !iconified;
        });

		
		//---- KEY CALLBACKS
		
		GLFW.glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (DBG)
                System.out.println("window key: " + title + " " + key + " " + scancode + " " + action + " " + mods);

            modifiers = mods;

            if (keyListener == null)
                return;
            if (action == GLFW.GLFW_PRESS)
                keyListener.keyPressed(GLFWWindow.this, mods, key, scancode, false);
            else if (action == GLFW.GLFW_REPEAT)
                keyListener.keyPressed(GLFWWindow.this, mods, key, scancode, true);
            else if (action == GLFW.GLFW_RELEASE)
                keyListener.keyReleased(GLFWWindow.this, mods, key, scancode);
        });
		

		//---- POINTER CALLBACKS
		
		GLFW.glfwSetCursorEnterCallback(window, (w, entered) -> {
            if (DBG)
                System.out.println("window pointer entered: " + title + " " + entered);

            if (pointerListener == null)
                return;
            if (entered)
                pointerListener.pointerEntered(GLFWWindow.this, modifiers, pointerPosition);
            else
                pointerListener.pointerExited(GLFWWindow.this, modifiers, pointerPosition);
        });

		GLFW.glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (DBG)
                System.out.println("window pointer moved: " + title + " " + xpos + " " + ypos);

            float sx = framebufferSize.x / windowSize.x;
            float sy = framebufferSize.y / windowSize.y;
            pointerPosition = new Vec2(sx * (float)xpos, sy * (float)(windowSize.y - ypos));

            if (pointerListener == null)
                return;
            if (pointerButtons == 0) {
                pointerListener.pointerMoved(GLFWWindow.this, modifiers, pointerPosition);
            } else {
                pointerDragged = true;
                pointerListener.pointerDragged(GLFWWindow.this, modifiers, pointerPosition);
            }
        });
		GLFW.glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (DBG)
                System.out.println("window pointer button: " + title + " " + button + " " + action + " " + mods);

            modifiers = mods;
            int mask = 1 << button;
            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
                pointerButtons |= mask;
                if (pointerListener != null)
                    pointerListener.pointerPressed(GLFWWindow.this, modifiers, pointerPosition, button);
            } else if (action == GLFW.GLFW_RELEASE) {
                pointerButtons &= ~mask;
                if (pointerListener != null)
                    pointerListener.pointerReleased(GLFWWindow.this, modifiers, pointerPosition, button);
            }
            if (pointerButtons == 0) {
                if (!pointerDragged && pointerListener != null)
                    pointerListener.pointerClicked(GLFWWindow.this, modifiers, pointerPosition, button);
                pointerDragged = false;
            }
        });

		GLFW.glfwSetScrollCallback(window, (w, xoffset, yoffset) -> {
            if (DBG)
                System.out.println("window pointer scrolled: " + title + " " + xoffset + " " + yoffset);

            if (pointerListener == null)
                return;
            pointerListener.pointerWheelMoved(GLFWWindow.this, modifiers, pointerPosition, new Vec2((float)xoffset, (float)-yoffset));
        });
	}
	
	private void checkMainThread() {
		if (!Platform.get().isMainThread())
			throw new IllegalThreadStateException("must be called from main thread, but called from " + Thread.currentThread());
	}
}
