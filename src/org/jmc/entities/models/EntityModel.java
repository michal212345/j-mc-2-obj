package org.jmc.entities.models;

import java.awt.Rectangle;

import org.jmc.BlockData;
import org.jmc.BlockMaterial;
import org.jmc.BlockTypes;
import org.jmc.Options;
import org.jmc.geom.Direction;
import org.jmc.geom.Transform;
import org.jmc.geom.UV;
import org.jmc.geom.Vertex;
import org.jmc.registry.NamespaceID;
import org.jmc.threading.ChunkProcessor;
import org.jmc.threading.ThreadChunkDeligate;


/**
 * Base class for the block model handlers.
 * These handlers are responsible for rendering the geometry that represents the blocks.
 */
public abstract class EntityModel
{
	protected NamespaceID blockId = NamespaceID.NULL;
	protected BlockMaterial materials = null;
	

	/**
	 * Id of the block this model will be rendering.
	 * This information may influence the behavior of the model.
	 */
	public void setBlockId(NamespaceID val)
	{
		this.blockId = val;
	}
	
	/**
	 * Set the materials for this block.
	 */
	public void setMaterials(BlockMaterial val)
	{
		this.materials = val;
	}


	/**
	 * Expand the materials to the full 6 side definition used by addBox
	 */
	protected NamespaceID[] getMtlSides(BlockData data, NamespaceID biome)
	{
		NamespaceID[] abbrMtls = materials.get(data.state, biome);
		
		NamespaceID[] mtlSides = new NamespaceID[6];
		if (abbrMtls.length < 2)
		{
			mtlSides[0] = abbrMtls[0];
			mtlSides[1] = abbrMtls[0];
			mtlSides[2] = abbrMtls[0];
			mtlSides[3] = abbrMtls[0];
			mtlSides[4] = abbrMtls[0];
			mtlSides[5] = abbrMtls[0];
		}
		else if (abbrMtls.length < 3)
		{
			mtlSides[0] = abbrMtls[0];
			mtlSides[1] = abbrMtls[1];
			mtlSides[2] = abbrMtls[1];
			mtlSides[3] = abbrMtls[1];
			mtlSides[4] = abbrMtls[1];
			mtlSides[5] = abbrMtls[0];
		}
		else if (abbrMtls.length < 6)
		{
			mtlSides[0] = abbrMtls[0];
			mtlSides[1] = abbrMtls[1];
			mtlSides[2] = abbrMtls[1];
			mtlSides[3] = abbrMtls[1];
			mtlSides[4] = abbrMtls[1];
			mtlSides[5] = abbrMtls[2];
		}
		else
		{
			mtlSides[0] = abbrMtls[0];
			mtlSides[1] = abbrMtls[1];
			mtlSides[2] = abbrMtls[2];
			mtlSides[3] = abbrMtls[3];
			mtlSides[4] = abbrMtls[4];
			mtlSides[5] = abbrMtls[5];
		}
		
		return mtlSides;
	}
	
	/**
	 * Helper method to check if the side of a cube needs to be drawn, based on 
	 * the occlusion type of the neighbouring block and whether or not the block
	 * is at the world (or selection) edge.
	 *  
	 * @param neighbour Id of the neighbouring block, or -1 if there is no 
	 * Neighbour (because the block is at the world edge)
	 * @param side Side to check
	 * @return true if side needs to be drawn
	 */
	protected boolean drawSide(Direction side, BlockData neighbour)
	{
		if (neighbour.id == NamespaceID.NULL)
			return Options.renderSides;
		
		if (neighbour.id.path.endsWith("air"))
			return true;
		
		switch(BlockTypes.get(neighbour).getOcclusion())
		{
			case FULL:
				return false;
			case NONE:
				return true;
			case TRANSPARENT:
				return !neighbour.id.equals(blockId);
			case BOTTOM:
				return side != Direction.UP;
			default:
				return false;
		}
	}

	
	/**
	 * Helper method to check which sides of a cube need to be drawn, based on 
	 * the occlusion type of the neighbouring blocks and whether or not the block
	 * is at the world (or selection) edge.
	 * 
	 * @param chunks World chunk data
	 * @param x Block x coordinate
	 * @param y Block y coordinate
	 * @param z Block z coordinate
	 * @return Whether to draw each side, in order TOP, FRONT, BACK, LEFT, RIGHT, BOTTOM
	 */
	protected boolean[] drawSides(ThreadChunkDeligate chunks, int x, int y, int z)
	{
		int xmin,xmax,ymin,ymax,zmin,zmax;
		Rectangle xy,xz;
		xy=chunks.getXYBoundaries();
		xz=chunks.getXZBoundaries();
		xmin=xy.x;
		xmax=xmin+xy.width-1;
		ymin=xy.y;
		ymax=ymin+xy.height-1;
		zmin=xz.y;
		zmax=zmin+xz.height-1;
		
		boolean sides[] = new boolean[6];

		sides[0] = drawSide(Direction.UP,    y==ymax ? new BlockData() : chunks.getBlockData(x, y+1, z));
		sides[1] = drawSide(Direction.NORTH,  z==zmin ? new BlockData() : chunks.getBlockData(x, y, z-1));
		sides[2] = drawSide(Direction.SOUTH,   z==zmax ? new BlockData() : chunks.getBlockData(x, y, z+1));
		sides[3] = drawSide(Direction.WEST,   x==xmin ? new BlockData() : chunks.getBlockData(x-1, y, z));
		sides[4] = drawSide(Direction.EAST,  x==xmax ? new BlockData() : chunks.getBlockData(x+1, y, z));
		sides[5] = drawSide(Direction.DOWN, y==ymin ? new BlockData() : chunks.getBlockData(x, y-1, z));

		return sides;
	}

	
	/**
	 * Helper method to add a box to given OBJFile.
	 * 
	 * @param obj OBJFile to add to
	 * @param xs Start x coordinate
	 * @param ys Start y coordinate
	 * @param zs Start z coordinate
	 * @param xe End x coordinate
	 * @param ye End y coordinate
	 * @param ze End z coordinate
	 * @param trans Transform to apply to the vertex coordinates. If null, no transform is applied 
	 * @param mtlSides Material for each side, in order TOP, FRONT, BACK, LEFT, RIGHT, BOTTOM
	 * @param uvSides Texture coordinates for each side, in order TOP, FRONT, BACK, LEFT, RIGHT, BOTTOM. If null, uses default
	 * coordinates for all sides. If an individual side is null, uses default coordinates for that side.
	 * @param drawSides Whether to draw each side, in order TOP, FRONT, BACK, LEFT, RIGHT, BOTTOM. If null, draws all sides.
	 */
	protected void addBox(ChunkProcessor obj, double xs, double ys, double zs, double xe, double ye, double ze, Transform trans, NamespaceID[] mtlSides, UV[][] uvSides, boolean[] drawSides)
	{
		Vertex[] vertices = new Vertex[4];

		if (drawSides == null || drawSides[0])
		{	// top
			vertices[0] = new Vertex(xs,ye,ze);
			vertices[1] = new Vertex(xe,ye,ze);
			vertices[2] = new Vertex(xe,ye,zs);
			vertices[3] = new Vertex(xs,ye,zs);
			obj.addFace(vertices, uvSides == null ? null : uvSides[0], trans, mtlSides[0]);
		}
		if (drawSides == null || drawSides[1])
		{	// front
			vertices[0] = new Vertex(xe,ys,zs);
			vertices[1] = new Vertex(xs,ys,zs);
			vertices[2] = new Vertex(xs,ye,zs);
			vertices[3] = new Vertex(xe,ye,zs);
			obj.addFace(vertices, uvSides == null ? null : uvSides[1], trans, mtlSides[1]);
		}
		if (drawSides == null || drawSides[2])
		{	// back
			vertices[0] = new Vertex(xs,ys,ze);
			vertices[1] = new Vertex(xe,ys,ze);
			vertices[2] = new Vertex(xe,ye,ze);
			vertices[3] = new Vertex(xs,ye,ze);
			obj.addFace(vertices, uvSides == null ? null : uvSides[2], trans, mtlSides[2]);
		}
		if (drawSides == null || drawSides[3])
		{	// left
			vertices[0] = new Vertex(xs,ys,zs);
			vertices[1] = new Vertex(xs,ys,ze);
			vertices[2] = new Vertex(xs,ye,ze);
			vertices[3] = new Vertex(xs,ye,zs);
			obj.addFace(vertices, uvSides == null ? null : uvSides[3], trans, mtlSides[3]);
		}
		if (drawSides == null || drawSides[4])
		{	// right
			vertices[0] = new Vertex(xe,ys,ze);
			vertices[1] = new Vertex(xe,ys,zs);
			vertices[2] = new Vertex(xe,ye,zs);
			vertices[3] = new Vertex(xe,ye,ze);
			obj.addFace(vertices, uvSides == null ? null : uvSides[4], trans, mtlSides[4]);
		}
		if (drawSides == null || drawSides[5])
		{	// bottom
			vertices[0] = new Vertex(xe,ys,ze);
			vertices[1] = new Vertex(xs,ys,ze);
			vertices[2] = new Vertex(xs,ys,zs);
			vertices[3] = new Vertex(xe,ys,zs);
			obj.addFace(vertices, uvSides == null ? null : uvSides[5], trans, mtlSides[5]);
		}
	}
	
	
	/**
	 * Adds the entity to the given OBJFile.
	 * 
	 * @param obj OBJFile to add the model to.
	 * @param transform transform applied to the given model
	 */
	public abstract void addEntity(ChunkProcessor obj, Transform transform);
	
}
