import { drawImage, expect, message, openChat, test } from './fixtures'

test('a big photo is scaled down, shown to the other person and can be deleted', async ({ createUser, signIn, directChat, api }) => {
  const alice = await createUser('Alice Marlowe')
  const bob = await createUser('Bob Hartley')
  const chat = await directChat(alice, bob, 'look at this')

  const alicePage = await signIn(alice)
  const bobPage = await signIn(bob)
  await openChat(alicePage, chat)
  await openChat(bobPage, chat)

  await alicePage.locator('form input[type=file]').setInputFiles({
    name: 'harbour.png', mimeType: 'image/png', buffer: await drawImage(alicePage, 4000, 3000),
  })
  await alicePage.locator('textarea').fill('The harbour, 1962')
  await alicePage.keyboard.press('Enter')

  const photo = bobPage.getByTitle('View photo')
  await expect(photo.locator('img')).toBeVisible()
  const { items } = await api('GET', `/api/conversations/${chat}/messages?limit=1`, { token: bob.token })
  expect(items[0].image).toMatchObject({ width: 2048, height: 1536 })
  await expect(bobPage.locator('nav a', { hasText: 'Alice Marlowe' })).toContainText('The harbour, 1962')

  await photo.click()
  await expect(bobPage.getByRole('dialog').locator('img')).toBeVisible()
  await bobPage.keyboard.press('Escape')
  await expect(bobPage.getByRole('dialog')).toHaveCount(0)

  const sent = message(alicePage, 'The harbour, 1962')
  await sent.hover()
  await sent.getByTitle('Delete').click()
  await alicePage.getByRole('button', { name: 'Delete for everyone' }).click()
  await expect(bobPage.getByText('This message was deleted.')).toBeVisible()
})

test('a new profile photo and name reach contacts without a reload', async ({ createUser, signIn, directChat }) => {
  const alice = await createUser('Alice Marlowe')
  const bob = await createUser('Bob Hartley')
  const chat = await directChat(alice, bob, 'hi')

  const bobPage = await signIn(bob)
  await openChat(bobPage, chat)
  const row = bobPage.locator('nav a', { hasText: 'Alice' })
  await expect(row).toBeVisible()

  const alicePage = await signIn(alice)
  await alicePage.goto('/profile')
  await alicePage.locator('input[type=file]').setInputFiles({
    name: 'me.jpg', mimeType: 'image/jpeg', buffer: await drawImage(alicePage, 600, 600, 'image/jpeg'),
  })
  await expect(alicePage.getByTitle('Change photo').locator('img')).toBeVisible()
  await alicePage.locator('input.field').fill('Alice Wren')
  await alicePage.locator('textarea').fill('Archivist. Collects maps.')
  await alicePage.getByRole('button', { name: 'Save' }).click()
  await expect(alicePage.getByText('Saved')).toBeVisible()

  await expect(bobPage.locator('nav a', { hasText: 'Alice Wren' }).locator('img')).toBeVisible()
  await expect(bobPage.locator('h1')).toContainText('Alice Wren')
  await bobPage.getByTitle('Details').click()
  await expect(bobPage.locator('aside', { hasText: 'Archivist. Collects maps.' })).toBeVisible()
})
