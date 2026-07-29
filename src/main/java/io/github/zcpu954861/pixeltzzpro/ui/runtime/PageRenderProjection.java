package io.github.zcpu954861.pixeltzzpro.ui.runtime;

import java.util.Objects;

/**
 * The part of page state that is allowed to replace the current widget tree.
 *
 * <p>Short-lived transport state, such as a request being pending or its result becoming
 * available, deliberately stays outside this projection. This keeps an otherwise unchanged page
 * from replaying every widget's entrance and hover animation. The content value should contain
 * every server-authoritative field that can change the rendered page, including exclusive-choice
 * occupancy.</p>
 */
public record PageRenderProjection<C, S>(
	C content,
	S status,
	String message
) {
	public PageRenderProjection {
		Objects.requireNonNull(status, "status");
		message = Objects.requireNonNull(message, "message");
	}

	public boolean differsFrom(final PageRenderProjection<C, S> other) {
		return !this.equals(Objects.requireNonNull(other, "other"));
	}
}
