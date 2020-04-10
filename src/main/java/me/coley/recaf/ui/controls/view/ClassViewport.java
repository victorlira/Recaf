package me.coley.recaf.ui.controls.view;

import javafx.application.Platform;
import me.coley.recaf.control.gui.GuiController;
import me.coley.recaf.decompile.DecompileImpl;
import me.coley.recaf.ui.controls.ClassEditor;
import me.coley.recaf.ui.controls.HexEditor;
import me.coley.recaf.ui.controls.popup.SuggestionWindow;
import me.coley.recaf.ui.controls.node.ClassNodePane;
import me.coley.recaf.ui.controls.text.JavaPane;
import me.coley.recaf.util.*;
import me.coley.recaf.workspace.History;
import me.coley.recaf.workspace.JavaResource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Multi-view wrapper for classes in resources.
 *
 * @author Matt
 */
public class ClassViewport extends EditorViewport {
	/**
	 * @param controller
	 * 		Controller context.
	 * @param resource
	 * 		Resource the file resides in.
	 * @param path
	 * 		Path to file.
	 */
	public ClassViewport(GuiController controller, JavaResource resource, String path) {
		super(controller, resource, path);
	}

	@Override
	protected History getHistory(String path) {
		return resource.getClassHistory(path);
	}

	@Override
	protected Map<String, byte[]> getMap() {
		return resource.getClasses();
	}

	@Override
	public void updateView() {
		switch(getClassMode()) {
			case DECOMPILE: {
				// Get or create pane
				JavaPane pane = null;
				if (getCenter() instanceof JavaPane) {
					pane = (JavaPane) getCenter();
				} else {
					pane = new JavaPane(controller, resource);
					pane.setWrapText(false);
					pane.setEditable(pane.canCompile() && resource.isPrimary());
					setCenter(pane);
				}
				// Decompile
				DecompileImpl decompiler = controller.config().decompile().decompiler;
				JavaPane finalPane = pane;
				long timeout = controller.config().decompile().timeout;
				boolean showSuggestions = controller.config().display().suggestClassWithErrors;
				pane.setText("// Decompiling class: " + path + "\n" +
						"// - Decompiler: " + decompiler.name() + "\n");
				// Actions
				Supplier<String> supplier = () -> {
					// SUPPLIER: Fetch decompiled code
					String decompile =
							decompiler.create(controller).decompile(path);
					return EscapeUtil.unescapeUnicode(decompile);
				};
				Consumer<String> consumer = decompile -> {
					// CONSUMER: Set decompiled text and check for errors
					// Show popup suggesting switching modes when the decompile has errors
					if (showSuggestions) {
						ThreadUtil.runJfxDelayed(1000, () -> {
							if(finalPane.getErrorHandler().hasErrors()) {
								SuggestionWindow.suggestAltDecompile(controller, this).show(this);
							}
						});
					}
					// Update text
					Platform.runLater(() -> {
						finalPane.setText(decompile);
						finalPane.forgetHistory();
					});
					// Sometimes the code analysis gets stuck on the initial commented out text...
					// This checks for getting stuck and forces an update. Hacky, but does the job.
					ThreadUtil.runJfxDelayed(600, () -> {
						if (!finalPane.getAnalyzedCode().getCode().equals(decompile)) {
							finalPane.appendText(" ");
						}
					});
				};
				Runnable timeoutAction = () -> {
					// TIMEOUT: Suggest another decompiler
					Platform.runLater(() -> {
						finalPane.appendText("// \n// Timed out after " + timeout + " ms\n// \n" +
								"// Suggestion: Change the decompiler or switch the class mode to " +
								ClassMode.TABLE.name());
					});
					// Show popup suggesting switching modes when the decompile fails
					if(showSuggestions) {
						ThreadUtil.runJfxDelayed(100, () -> {
							SuggestionWindow.suggestTimeoutDecompile(controller, this).show(this);
						});
					}
				};
				Consumer<Throwable> handler = t -> {
					// ERROR-HANDLER: Print decompile error
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					t.printStackTrace(pw);
					String decompile = LangUtil.translate("decompile.fail") + "\n\nError Message: "
							+ t.getMessage() + "\n\nStackTrace:\n" + sw.toString();
					finalPane.setEditable(false);
					// Show popup suggesting switching modes when the decompile fails
					if(showSuggestions) {
						ThreadUtil.runJfxDelayed(100, () -> {
							SuggestionWindow.suggestFailedDecompile(controller, this).show(this);
						});
					}
					// Update text
					Platform.runLater(() -> {
						finalPane.appendText("\n/*\n" + decompile + "\n*/");
						finalPane.forgetHistory();
					});
				};
				// Run actions
				ThreadUtil.runJfx(supplier, timeout, timeoutAction, consumer, handler);
				break;
			}
			case TABLE: {
				// TODO: like how Recaf was in 1.X
				ClassNodePane pane = null;
				if(getCenter() instanceof ClassNodePane) {
					pane = (ClassNodePane) getCenter();
					pane.setup();
				} else {
					ClassReader cr = controller.getWorkspace().getClassReader(path);
					ClassNode node = ClassUtil.getNode(cr, ClassReader.SKIP_FRAMES);
					pane = new ClassNodePane(controller, node);
					setCenter(pane);
				}
				break;
			}
			case HEX:
			default:
				HexEditor hex = new HexEditor(last);
				hex.setContentCallback(array -> current = array);
				hex.setEditable(resource.isPrimary());
				setCenter(hex);
				break;
		}
	}

	@Override
	public void save() {
		// Handle saving for editing decompiled java
		if (getCenter() instanceof JavaPane) {
			try {
				Map<String, byte[]> map = ((ClassEditor) getCenter()).save(path);
				current = map.get(path);
				// Save other inners
				map.remove(path);
				JavaResource resource = controller.getWorkspace().getPrimary();
				map.forEach((key, value) -> {
					resource.getClasses().put(key, value);
					resource.getClassHistory(key).push(value);
				});
			} catch(UnsupportedOperationException ex) {
				Log.warn("Recompiling not supported. Please run Recaf with a JDK.", path);
				return;
			} catch(Exception ex) {
				Log.error("Failed recompiling code for '{}'", path);
				return;
			}
		} else if (getCenter() instanceof ClassNodePane) {
			try {
				current = ((ClassEditor) getCenter()).save(path).get(path);
			} catch(Exception ex) {
				Log.error(ex, "Failed saving changes for '{}'", path);
				return;
			}
		}
		// Save content
		super.save();
	}

	/**
	 * Jump to the definition of the given member.
	 *
	 * @param name
	 * 		Member name.
	 * @param desc
	 * 		Member descriptor.
	 */
	public void selectMember(String name, String desc) {
		if (getCenter() instanceof  ClassEditor)
			((ClassEditor)getCenter()).selectMember(name, desc);
	}


	/**
	 * @return Mode that indicated which view to use for modifying classes.
	 */
	private ClassMode getClassMode() {
		return controller.config().display().classEditorMode;
	}

	/**
	 * Viewport editor type.
	 */
	public enum ClassMode {
		DECOMPILE, TABLE, HEX
	}
}
