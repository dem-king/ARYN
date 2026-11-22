export interface GoodsProfileNutritionForm {
  amount: string;
  name: string;
  nrv: string;
  unit: string;
}

export interface GoodsProfileForm {
  allergens: string;
  certifications: string[];
  ingredients: string;
  nutrition: GoodsProfileNutritionForm[];
  standard: string;
}

export interface GoodsDetailSectionForm {
  content: string;
  imageUrl: string;
  title: string;
}

export const emptyGoodsProfile = (): GoodsProfileForm => ({
  ingredients: '',
  allergens: '',
  standard: '',
  certifications: [],
  nutrition: [],
});

export const emptyGoodsDetailSection = (): GoodsDetailSectionForm => ({
  title: '',
  content: '',
  imageUrl: '',
});

const text = (value: unknown) =>
  typeof value === 'string' ? value.trim() : '';

export const normalizeGoodsProfileForm = (raw: unknown): GoodsProfileForm => {
  if (!raw || typeof raw !== 'object') {
    return emptyGoodsProfile();
  }
  const source = raw as Record<string, unknown>;
  const nutrition = Array.isArray(source.nutrition)
    ? source.nutrition
        .filter(
          (item): item is Record<string, unknown> =>
            Boolean(item) && typeof item === 'object',
        )
        .map((item) => ({
          name: text(item.name),
          amount: text(item.amount),
          unit: text(item.unit),
          nrv: text(item.nrv),
        }))
        .filter((item) => Boolean(item.name))
    : [];
  const certifications = Array.isArray(source.certifications)
    ? [
        ...new Set(
          source.certifications.map((item) => text(item)).filter(Boolean),
        ),
      ]
    : [];
  return {
    ingredients: text(source.ingredients),
    allergens: text(source.allergens),
    standard: text(source.standard),
    certifications,
    nutrition,
  };
};

export const normalizeDetailSectionForms = (
  raw: unknown,
): GoodsDetailSectionForm[] => {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw
    .filter(
      (item): item is Record<string, unknown> =>
        Boolean(item) && typeof item === 'object',
    )
    .map((item) => ({
      title: text(item.title),
      content: text(item.content),
      imageUrl: text(item.imageUrl),
    }))
    .filter((item) => Boolean(item.title || item.content || item.imageUrl));
};
