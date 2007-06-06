package net.jxta.netmap;

import prefuse.render.AbstractShapeRenderer;
import prefuse.render.LabelRenderer;
import prefuse.render.ShapeRenderer;
import prefuse.visual.VisualItem;

import java.awt.Graphics2D;
import java.awt.Shape;

public class LabelShapeRenderer extends AbstractShapeRenderer {

    private ShapeRenderer nodeR;

    private LabelRenderer m_labelRenderer;

    public LabelShapeRenderer(String textField, ShapeRenderer nodeR) {
        this.nodeR = nodeR;
        m_labelRenderer = new LabelRenderer(textField);
        m_labelRenderer.setRenderType(AbstractShapeRenderer.RENDER_TYPE_NONE);
    }

    @Override
    public void render(Graphics2D g, VisualItem item) {
        nodeR.render(g, item);
        m_labelRenderer.render(g, item);
    }


    @Override
    ///XXX getRawShape should return the "raw", not transformed shape!
    //Actually, it just returns the (already transformed) ShapeRenderers shape.
    protected Shape getRawShape(VisualItem item) {
        return nodeR.getShape(item);
    }
}
