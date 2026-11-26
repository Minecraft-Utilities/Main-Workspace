/**
 * The fields the skin viewer needs from whoever owns the skin. A Java player and a Bedrock player
 * both satisfy this, while a Bedrock player has no cape.
 */
export type SkinViewerSubject = {
  username: string;
  cape?: unknown;
};
